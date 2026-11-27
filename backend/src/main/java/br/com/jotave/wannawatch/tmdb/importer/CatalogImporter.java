package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import br.com.jotave.wannawatch.tmdb.client.TmdbProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Importa o catálogo da TMDB: gêneros → listas configuradas → detalhes de cada título → banco.
 * Só uma importação roda por vez. Os detalhes são buscados em paralelo (respeitando o teto de
 * requisições do {@link TmdbClient}) e gravados um a um, cada título na sua transação.
 */
@Service
public class CatalogImporter {

    private static final Logger log = LoggerFactory.getLogger(CatalogImporter.class);

    private final TmdbClient tmdb;
    private final TmdbProperties tmdbProps;
    private final ImportProperties props;
    private final CatalogWriter writer;
    private final TitleFetcher fetcher;
    private final ApplicationEventPublisher events;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ImportResult> lastResult = new AtomicReference<>();

    public CatalogImporter(TmdbClient tmdb, TmdbProperties tmdbProps, ImportProperties props, CatalogWriter writer,
                           TitleFetcher fetcher, ApplicationEventPublisher events) {
        this.events = events;
        this.tmdb = tmdb;
        this.tmdbProps = tmdbProps;
        this.props = props;
        this.writer = writer;
        this.fetcher = fetcher;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<ImportResult> lastResult() {
        return Optional.ofNullable(lastResult.get());
    }

    /** Dispara em segundo plano. Devolve false se já houver uma importação rodando. */
    public boolean startAsync(String reason) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Thread.ofVirtual().name("tmdb-import").start(() -> {
            try {
                doRun(reason);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /** Roda na thread atual (usado pelo agendamento e pelos testes). */
    public ImportResult run(String reason) {
        if (!running.compareAndSet(false, true)) {
            log.info("Importação ignorada ({}): já existe uma rodando", reason);
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
            log.warn("Importação ({}) pulada: TMDB_API_TOKEN não configurado", reason);
            return finish(new ImportResult(ImportResult.Status.SKIPPED, start, Instant.now(),
                    0, 0, 0, 0, Map.of(), "TMDB_API_TOKEN não configurado"));
        }
        log.info("Importação da TMDB iniciada ({})", reason);
        try {
            int genreCount = writer.upsertGenres(tmdb.movieGenres(), tmdb.tvGenres(), props.genreNames());

            Map<String, List<TitleRef>> lists = new LinkedHashMap<>();
            for (ImportProperties.ListSpec spec : props.lists()) {
                lists.put(spec.key(), fetchList(spec));
            }
            LinkedHashSet<TitleRef> unique = new LinkedHashSet<>();
            lists.values().forEach(unique::addAll);
            log.info("{} títulos únicos em {} listas; buscando detalhes", unique.size(), lists.size());

            TitleFetcher.Fetched details = fetcher.fetch(unique);
            Map<TitleRef, ImportedTitle> fetched = details.titles();
            int failed = details.failed();

            Instant now = Instant.now();
            Map<TitleRef, Long> saved = new LinkedHashMap<>();
            int skipped = 0;
            for (Map.Entry<TitleRef, ImportedTitle> e : fetched.entrySet()) {
                if (e.getValue().adult()) {
                    skipped++;
                    continue;
                }
                try {
                    saved.put(e.getKey(), writer.upsertTitle(e.getValue(), now, true));
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Falha ao gravar {} {}: {}", e.getKey().type(), e.getKey().id(), ex.getMessage());
                }
            }

            Map<String, Integer> sizes = new LinkedHashMap<>();
            lists.forEach((key, refs) -> {
                List<Long> ids = refs.stream().map(saved::get).filter(java.util.Objects::nonNull).toList();
                writer.replaceRanking(key, ids, now);
                sizes.put(key, ids.size());
            });

            ImportResult.Status status = failed == 0 ? ImportResult.Status.SUCCESS : ImportResult.Status.PARTIAL;
            ImportResult result = new ImportResult(status, start, Instant.now(), genreCount,
                    saved.size(), skipped, failed, sizes, null);
            log.info("Importação concluída: {} títulos gravados, {} pulados, {} com falha, em {} s",
                    saved.size(), skipped, failed,
                    java.time.Duration.between(start, result.finishedAt()).toSeconds());
            finish(result);
            // quem depende do catálogo (ex.: o índice de recomendação) se atualiza
            events.publishEvent(new CatalogImportedEvent(result));
            return result;
        } catch (RuntimeException ex) {
            log.error("Importação falhou", ex);
            return finish(new ImportResult(ImportResult.Status.FAILED, start, Instant.now(),
                    0, 0, 0, 0, Map.of(), ex.getMessage()));
        }
    }

    private ImportResult finish(ImportResult r) {
        lastResult.set(r);
        return r;
    }

    private List<TitleRef> fetchList(ImportProperties.ListSpec spec) {
        LinkedHashSet<TitleRef> refs = new LinkedHashSet<>();
        for (int page = 1; page <= spec.pages(); page++) {
            TmdbDto.Page<TmdbDto.ListItem> p = tmdb.list(spec.path(), page);
            for (TmdbDto.ListItem item : p.results()) {
                if (item.id() == null || Boolean.TRUE.equals(item.adult())) continue;
                MediaType type = spec.mediaType() != null ? spec.mediaType() : parseType(item.mediaType());
                if (type != null) refs.add(new TitleRef(item.id(), type));
            }
            if (page >= p.totalPages()) break;
        }
        return new ArrayList<>(refs);
    }

    private static MediaType parseType(String tmdbType) {
        if ("movie".equals(tmdbType)) return MediaType.MOVIE;
        if ("tv".equals(tmdbType)) return MediaType.SERIES;
        return null; // "person" e afins
    }

}
