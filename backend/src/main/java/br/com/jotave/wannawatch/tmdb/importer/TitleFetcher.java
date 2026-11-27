package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbException;
import br.com.jotave.wannawatch.tmdb.client.TmdbProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Busca os detalhes de vários títulos na TMDB em paralelo (até {@code max-concurrency} ao mesmo
 * tempo; o teto de requisições por segundo fica no {@link TmdbClient}) e já converte para
 * {@link ImportedTitle}. Usado pelas três importações: listas, export e busca.
 */
@Component
class TitleFetcher {

    private static final Logger log = LoggerFactory.getLogger(TitleFetcher.class);

    private final TmdbClient tmdb;
    private final TmdbProperties props;
    private final TmdbMapper mapper;

    TitleFetcher(TmdbClient tmdb, TmdbProperties props) {
        this.tmdb = tmdb;
        this.props = props;
        this.mapper = new TmdbMapper(props.language(), props.region());
    }

    /** Resultado na ordem pedida; quem falhou (inclusive 404) fica de fora e é contado em {@code failed}. */
    record Fetched(Map<TitleRef, ImportedTitle> titles, int failed) {}

    Fetched fetch(Collection<TitleRef> refs) {
        Semaphore permits = new Semaphore(Math.max(1, props.maxConcurrency()));
        Map<TitleRef, Future<ImportedTitle>> futures = new LinkedHashMap<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TitleRef ref : refs) {
                futures.put(ref, pool.submit(() -> {
                    permits.acquire();
                    try {
                        return ref.type() == MediaType.MOVIE
                                ? mapper.fromMovie(tmdb.movie(ref.id()))
                                : mapper.fromTv(tmdb.tv(ref.id()));
                    } finally {
                        permits.release();
                    }
                }));
            }
        }
        Map<TitleRef, ImportedTitle> out = new LinkedHashMap<>();
        int failed = 0;
        for (Map.Entry<TitleRef, Future<ImportedTitle>> e : futures.entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().get());
            } catch (Exception ex) {
                failed++;
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof TmdbException te && te.isNotFound()) {
                    log.info("{} {} não existe mais na TMDB", e.getKey().type(), e.getKey().id());
                } else {
                    log.warn("Falha ao buscar {} {}: {}", e.getKey().type(), e.getKey().id(), cause.getMessage());
                }
            }
        }
        return new Fetched(out, failed);
    }
}
