package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.ReelCard;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.ReelVideo;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.recommendation.application.FeedSnapshots;
import br.com.jotave.wannawatch.recommendation.application.PersonalizedFeeds;
import br.com.jotave.wannawatch.recommendation.application.RecommendationProperties;
import br.com.jotave.wannawatch.recommendation.application.RecommendationService;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import br.com.jotave.wannawatch.shared.web.Cursor;
import br.com.jotave.wannawatch.shared.web.CursorResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feeds com cursor: Reels e deck do Swipe. Os dois só trazem títulos com sinopse, que é o
 * conteúdo principal dessas telas.
 *
 * <p>Sem login: Reels por popularidade e Swipe aleatório, mas fixo por seed. Com login, os dois
 * são personalizados ({@link PersonalizedFeeds}): a primeira página monta a ordem inteira e guarda
 * num snapshot ({@link FeedSnapshots}), e o cursor passa a ser a posição nele. Assim a paginação
 * não pula nem repete nada enquanto o score muda com os swipes.
 *
 * <p>No Reels, quem tem vídeo aprovado ({@link ReelVideos}) ganha um reforço na ordem e vem com o
 * vídeo; o resto continua sendo reel de sinopse.
 */
@Service
@Transactional(readOnly = true)
public class FeedService {

    private final JdbcClient jdbc;
    private final TitleCards cards;
    private final PersonalizedFeeds personalized;
    private final FeedSnapshots snapshots;
    private final RecommendationService recommendations;
    private final ReelVideos videos;
    private final RecommendationProperties props;

    public FeedService(JdbcClient jdbc, TitleCards cards, PersonalizedFeeds personalized, FeedSnapshots snapshots,
                       RecommendationService recommendations, ReelVideos videos, RecommendationProperties props) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.personalized = personalized;
        this.snapshots = snapshots;
        this.recommendations = recommendations;
        this.videos = videos;
        this.props = props;
    }

    public CursorResponse<ReelCard> reels(Optional<AuthUser> user, String cursor, int size, MediaType type,
                                          String genre) {
        CursorResponse<TitleCard> page;
        if (user.isPresent()) {
            long me = user.get().id();
            page = fromSnapshot(me, FeedSnapshots.key(me, "reels", "", type, genre), cursor, size,
                    () -> personalized.reelsOrder(me, candidates(type, genre), videos.titlesWithVideo()));
        } else {
            page = reels(cursor, size, type, genre);
        }
        Map<Long, ReelVideo> byTitle = videos.forTitles(page.items().stream().map(TitleCard::id).toList());
        return new CursorResponse<>(page.items().stream().map(c -> new ReelCard(c, byTitle.get(c.id()))).toList(),
                page.nextCursor());
    }

    public CursorResponse<TitleCard> swipeDeck(Optional<AuthUser> user, String seed, String cursor, int size,
                                               MediaType type, String genre) {
        if (user.isPresent()) {
            long me = user.get().id();
            return fromSnapshot(me, FeedSnapshots.key(me, "swipe", seed, type, genre), cursor, size,
                    () -> personalized.swipeOrder(me, candidates(type, genre), seed));
        }
        return swipeDeck(seed, cursor, size, type, genre);
    }

    /**
     * Página de um feed personalizado. Primeira página (ou snapshot expirado): calcula a ordem e
     * guarda. O cursor é "p&lt;posição&gt;" no formato de sempre do {@link Cursor}.
     */
    private CursorResponse<TitleCard> fromSnapshot(long userId, String key, String cursor, int size,
                                                   Supplier<List<Long>> order) {
        Cursor after = Cursor.decode(cursor);
        int from = 0;
        List<Long> ids = null;
        if (after != null) {
            if (!after.key().startsWith("p")) throw new BadRequestException("Cursor inválido.");
            ids = snapshots.get(key).orElse(null);
            if (ids != null) from = position(after);
        }
        if (ids == null) ids = snapshots.put(key, order.get());
        from = Math.min(from, ids.size());
        int to = Math.min(ids.size(), from + size);
        List<Long> page = ids.subList(from, to);
        String next = to < ids.size() ? new Cursor("p" + to, page.getLast()).encode() : null;
        return new CursorResponse<>(cards.load(page, recommendations.reasons(userId, page)), next);
    }

    private static int position(Cursor cursor) {
        try {
            return Math.max(0, Integer.parseInt(cursor.key().substring(1)));
        } catch (NumberFormatException e) {
            throw new BadRequestException("Cursor inválido.");
        }
    }

    /** Todos os títulos dos feeds com sinopse que passam nos filtros (poucas centenas). */
    private List<Long> candidates(MediaType type, String genre) {
        Map<String, Object> params = new HashMap<>();
        String sql = "SELECT t.id FROM title t WHERE t.in_feeds AND t.overview IS NOT NULL" + SqlFilters.typeAndGenre(type, genre, params);
        return jdbc.sql(sql).params(params).query(Long.class).list();
    }

    /**
     * Reels sem login: {@code reelsPopularity·percentil da popularidade + reelsVideoBoost} (se tiver
     * vídeo), a mesma conta do personalizado sem a parte do gosto. Cursor = (chave, id).
     */
    private CursorResponse<TitleCard> reels(String cursor, int size, MediaType type, String genre) {
        Cursor after = Cursor.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("wp", props.feed().reelsPopularity());
        params.put("wv", props.feed().reelsVideoBoost());
        StringBuilder sql = new StringBuilder("""
                SELECT id, k FROM (
                    SELECT t.id,
                           :wp * percent_rank() OVER (ORDER BY t.popularity)
                           + CASE WHEN EXISTS (SELECT 1 FROM title_video v WHERE v.title_id = t.id AND\s""");
        sql.append(ReelVideos.PLAYABLE).append(") THEN :wv ELSE 0 END AS k");
        sql.append(" FROM title t WHERE t.in_feeds AND t.overview IS NOT NULL");
        sql.append(SqlFilters.typeAndGenre(type, genre, params));
        sql.append(") r");
        if (after != null) {
            sql.append(" WHERE (k, id) < (:ck, :cid)");
            params.put("ck", after.keyAsDouble());
            params.put("cid", after.id());
        }
        sql.append(" ORDER BY k DESC, id DESC LIMIT :limit");
        params.put("limit", size + 1);

        List<Row> rows = jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new Row(rs.getLong("id"), Double.toString(rs.getDouble("k"))))
                .list();
        return page(rows, size);
    }

    public CursorResponse<TitleCard> swipeDeck(String seed, String cursor, int size, MediaType type, String genre) {
        Cursor after = Cursor.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("seed", seed);
        StringBuilder sql = new StringBuilder("""
                SELECT id, h FROM (
                    SELECT t.id, md5(:seed || ':' || t.id) AS h
                    FROM title t WHERE t.in_feeds AND t.overview IS NOT NULL""");
        sql.append(SqlFilters.typeAndGenre(type, genre, params));
        sql.append(") s");
        if (after != null) {
            sql.append(" WHERE (h, id) > (:ch, :cid)");
            params.put("ch", after.key());
            params.put("cid", after.id());
        }
        sql.append(" ORDER BY h, id LIMIT :limit");
        params.put("limit", size + 1);

        List<Row> rows = jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("h")))
                .list();
        return page(rows, size);
    }

    private CursorResponse<TitleCard> page(List<Row> rows, int size) {
        boolean hasMore = rows.size() > size;
        List<Row> pageRows = hasMore ? rows.subList(0, size) : rows;
        String next = hasMore ? new Cursor(pageRows.getLast().key(), pageRows.getLast().id()).encode() : null;
        return new CursorResponse<>(cards.load(pageRows.stream().map(Row::id).toList()), next);
    }

    private record Row(long id, String key) {}
}
