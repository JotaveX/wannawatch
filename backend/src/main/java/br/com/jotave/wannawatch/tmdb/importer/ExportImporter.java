package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbExportClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Catálogo grande para a busca: pega os filmes e séries mais populares do export diário da TMDB
 * e importa os que ainda não estão no banco, com {@code in_feeds = false} (aparecem na busca, nos
 * detalhes e em Filmes/Séries, mas não nos feeds nem na recomendação).
 *
 * <p>A primeira carga é longa (100 mil títulos a 20 req/s dá umas 1h30); depois cada rodada só
 * busca o que entrou no top desde a última. Grava em lotes, então pode ser interrompida e
 * continua de onde parou. Só uma roda por vez.
 */
@Service
public class ExportImporter {

    private static final Logger log = LoggerFactory.getLogger(ExportImporter.class);

    private final TmdbClient tmdb;
    private final TmdbExportClient exports;
    private final TmdbProperties tmdbProps;
    private final ImportProperties props;
    private final CatalogWriter writer;
    private final TitleFetcher fetcher;
    private final JdbcClient jdbc;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ImportResult> lastResult = new AtomicReference<>();
    private final AtomicReference<String> progress = new AtomicReference<>();

    public ExportImporter(TmdbClient tmdb, TmdbExportClient exports, TmdbProperties tmdbProps, ImportProperties props,
                          CatalogWriter writer, TitleFetcher fetcher, JdbcClient jdbc) {
        this.tmdb = tmdb;
        this.exports = exports;
        this.tmdbProps = tmdbProps;
        this.props = props;
        this.writer = writer;
        this.fetcher = fetcher;
        this.jdbc = jdbc;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Onde a rodada atual está (ex.: "MOVIE 3200/41000"), ou vazio se nenhuma estiver rodando. */
    public Optional<String> progress() {
        return running.get() ? Optional.ofNullable(progress.get()) : Optional.empty();
    }

    public Optional<ImportResult> lastResult() {
        return Optional.ofNullable(lastResult.get());
    }

    /** Dispara em segundo plano. Devolve false se já houver uma rodando. */
    public boolean startAsync(String reason) {
        if (!running.compareAndSet(false, true)) return false;
        Thread.ofVirtual().name("tmdb-export-import").start(() -> {
            try {
                doRun(reason);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /** Roda na thread atual (agendamento e testes). */
    public ImportResult run(String reason) {
        if (!running.compareAndSet(false, true)) {
            log.info("Importação do export ignorada ({}): já existe uma rodando", reason);
            return lastResult.get();
        }
        try {
            return doRun(reason);
        } finally {
            running.set(false);
        }
    }

    private ImportResult doRun(String reason) {
        Instant start = Instant.now();
        if (!tmdbProps.hasToken()) {
            log.warn("Importação do export ({}) pulada: TMDB_API_TOKEN não configurado", reason);
            return finish(new ImportResult(ImportResult.Status.SKIPPED, start, Instant.now(),
                    0, 0, 0, 0, Map.of(), "TMDB_API_TOKEN não configurado"));
        }
        ImportProperties.Export cfg = props.export();
        log.info("Importação do export da TMDB iniciada ({}): até {} filmes e {} séries",
                reason, cfg.maxMovies(), cfg.maxSeries());
        try {
            // os gêneros precisam existir antes dos títulos (banco novo)
            int genreCount = writer.upsertGenres(tmdb.movieGenres(), tmdb.tvGenres(), props.genreNames());
            Map<String, Integer> sizes = new LinkedHashMap<>();
            int[] totals = new int[3]; // gravados, pulados, falhas
            importType(MediaType.MOVIE, cfg.maxMovies(), cfg, totals, sizes);
            importType(MediaType.SERIES, cfg.maxSeries(), cfg, totals, sizes);

            ImportResult.Status status = totals[2] == 0 ? ImportResult.Status.SUCCESS : ImportResult.Status.PARTIAL;
            ImportResult result = new ImportResult(status, start, Instant.now(), genreCount,
                    totals[0], totals[1], totals[2], sizes, null);
            log.info("Importação do export concluída: {} títulos novos, {} pulados, {} com falha, em {} min",
                    totals[0], totals[1], totals[2], Duration.between(start, result.finishedAt()).toMinutes());
            return finish(result);
        } catch (RuntimeException ex) {
            log.error("Importação do export falhou", ex);
            return finish(new ImportResult(ImportResult.Status.FAILED, start, Instant.now(),
                    0, 0, 0, 0, Map.of(), ex.getMessage()));
        }
    }

    private void importType(MediaType type, int limit, ImportProperties.Export cfg, int[] totals,
                            Map<String, Integer> sizes) {
        if (limit <= 0) return;
        List<TmdbExportClient.Entry> top = exports.mostPopular(cfg.baseUrl(), type, limit);
        Set<Integer> existing = new HashSet<>(jdbc.sql("SELECT external_id FROM title WHERE media_type = :type")
                .param("type", type.name()).query(Integer.class).list());
        List<TitleRef> missing = top.stream()
                .filter(e -> !existing.contains(e.id()))
                .map(e -> new TitleRef(e.id(), type))
                .toList();
        log.info("Export de {}: {} no top, {} já no banco, {} para buscar",
                type, top.size(), top.size() - missing.size(), missing.size());

        int saved = 0;
        for (int from = 0; from < missing.size(); from += cfg.batchSize()) {
            List<TitleRef> batch = missing.subList(from, Math.min(missing.size(), from + cfg.batchSize()));
            TitleFetcher.Fetched fetched = fetcher.fetch(batch);
            totals[2] += fetched.failed();
            Instant now = Instant.now();
            for (Map.Entry<TitleRef, ImportedTitle> e : fetched.titles().entrySet()) {
                if (e.getValue().adult()) {
                    totals[1]++;
                    continue;
                }
                try {
                    writer.upsertTitle(e.getValue(), now, false);
                    saved++;
                } catch (RuntimeException ex) {
                    totals[2]++;
                    log.warn("Falha ao gravar {} {}: {}", type, e.getKey().id(), ex.getMessage());
                }
            }
            int done = from + batch.size();
            progress.set(type + " " + done + "/" + missing.size());
            if (done % (cfg.batchSize() * 10) == 0 || done == missing.size()) {
                log.info("Export de {}: {}/{} buscados", type, done, missing.size());
            }
        }
        totals[0] += saved;
        sizes.put(type == MediaType.MOVIE ? "export_movie" : "export_series", saved);
    }

    private ImportResult finish(ImportResult r) {
        lastResult.set(r);
        return r;
    }
}
