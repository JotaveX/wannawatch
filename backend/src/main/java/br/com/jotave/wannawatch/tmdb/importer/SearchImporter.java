package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.application.MissingTitleLookup;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import br.com.jotave.wannawatch.tmdb.client.TmdbException;
import br.com.jotave.wannawatch.tmdb.client.TmdbProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Busca na TMDB o que a busca daqui não achou: 1 requisição na busca da TMDB + 1 por título novo
 * (no máximo {@code max-titles}). Os títulos entram só para a busca ({@code in_feeds = false}).
 *
 * <p>A busca é aberta a visitantes, então: a mesma consulta não vai à TMDB de novo dentro do
 * {@code cache-ttl}, e há um teto de consultas por minuto somando todo mundo. Qualquer erro da
 * TMDB só é registrado; a busca devolve o que já tinha.
 */
@Service
public class SearchImporter implements MissingTitleLookup {

    private static final Logger log = LoggerFactory.getLogger(SearchImporter.class);
    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_CACHED_QUERIES = 10_000;

    private final TmdbClient tmdb;
    private final TmdbProperties tmdbProps;
    private final ImportProperties.Search cfg;
    private final CatalogWriter writer;
    private final TitleFetcher fetcher;
    private final JdbcClient jdbc;
    private final Clock clock;

    private final Map<String, Instant> recentQueries = new ConcurrentHashMap<>();
    private final Deque<Instant> recentCalls = new ArrayDeque<>();

    public SearchImporter(TmdbClient tmdb, TmdbProperties tmdbProps, ImportProperties props, CatalogWriter writer,
                          TitleFetcher fetcher, JdbcClient jdbc, Clock clock) {
        this.tmdb = tmdb;
        this.tmdbProps = tmdbProps;
        this.cfg = props.search();
        this.writer = writer;
        this.fetcher = fetcher;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean fetchMissing(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        if (!cfg.enabled() || !tmdbProps.hasToken() || key.length() < MIN_QUERY_LENGTH) return false;
        Instant now = clock.instant();
        if (askedRecently(key, now) || !withinRateLimit(now)) return false;
        recentQueries.put(key, now);

        try {
            List<TitleRef> missing = missingRefs(tmdb.searchMulti(query.trim()));
            if (missing.isEmpty()) return false;
            TitleFetcher.Fetched fetched = fetcher.fetch(missing);
            int saved = 0;
            for (ImportedTitle t : fetched.titles().values()) {
                if (t.adult()) continue;
                try {
                    writer.upsertTitle(t, now, false);
                    saved++;
                } catch (RuntimeException ex) {
                    log.warn("Falha ao gravar {} {} vindo da busca: {}", t.mediaType(), t.externalId(), ex.getMessage());
                }
            }
            log.info("Busca \"{}\" na TMDB: {} títulos novos", key, saved);
            return saved > 0;
        } catch (TmdbException ex) {
            log.warn("Busca \"{}\" na TMDB falhou: {}", key, ex.getMessage());
            return false;
        }
    }

    /** Filmes e séries com pôster, na ordem da TMDB, que ainda não estão no banco. */
    private List<TitleRef> missingRefs(TmdbDto.Page<TmdbDto.SearchItem> page) {
        List<TitleRef> refs = new ArrayList<>();
        if (page == null || page.results() == null) return refs;
        for (TmdbDto.SearchItem item : page.results()) {
            if (refs.size() >= cfg.maxTitles()) break;
            if (item.id() == null || Boolean.TRUE.equals(item.adult()) || item.posterPath() == null) continue;
            MediaType type = "movie".equals(item.mediaType()) ? MediaType.MOVIE
                    : "tv".equals(item.mediaType()) ? MediaType.SERIES : null;
            if (type == null || exists(item.id(), type)) continue;
            refs.add(new TitleRef(item.id(), type));
        }
        return refs;
    }

    private boolean exists(int externalId, MediaType type) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM title WHERE external_id = :id AND media_type = :type)")
                .param("id", externalId).param("type", type.name()).query(Boolean.class).single();
    }

    /** True se a mesma consulta já foi à TMDB dentro do cache-ttl. */
    private boolean askedRecently(String key, Instant now) {
        Instant cutoff = now.minus(cfg.cacheTtl());
        if (recentQueries.size() > MAX_CACHED_QUERIES) {
            recentQueries.values().removeIf(at -> at.isBefore(cutoff));
            if (recentQueries.size() > MAX_CACHED_QUERIES) recentQueries.clear();
        }
        Instant previous = recentQueries.get(key);
        return previous != null && previous.isAfter(cutoff);
    }

    private synchronized boolean withinRateLimit(Instant now) {
        Instant windowStart = now.minus(Duration.ofMinutes(1));
        while (!recentCalls.isEmpty() && recentCalls.peekFirst().isBefore(windowStart)) recentCalls.pollFirst();
        if (recentCalls.size() >= cfg.maxPerMinute()) {
            log.info("Busca na TMDB pulada: teto de {} por minuto atingido", cfg.maxPerMinute());
            return false;
        }
        recentCalls.addLast(now);
        return true;
    }
}
