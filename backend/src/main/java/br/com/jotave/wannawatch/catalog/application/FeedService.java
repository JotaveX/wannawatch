package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.shared.web.Cursor;
import br.com.jotave.wannawatch.shared.web.CursorResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feeds com cursor: Reels (por popularidade) e deck do Swipe (aleatório, mas fixo por seed).
 * Os dois só trazem títulos com sinopse, que é o conteúdo principal dessas telas.
 */
@Service
@Transactional(readOnly = true)
public class FeedService {

    private final JdbcClient jdbc;
    private final TitleCards cards;

    public FeedService(JdbcClient jdbc, TitleCards cards) {
        this.jdbc = jdbc;
        this.cards = cards;
    }

    public CursorResponse<TitleCard> reels(String cursor, int size, MediaType type, String genre) {
        Cursor after = Cursor.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder("SELECT t.id, t.popularity FROM title t WHERE t.overview IS NOT NULL");
        sql.append(SqlFilters.typeAndGenre(type, genre, params));
        if (after != null) {
            sql.append(" AND (t.popularity, t.id) < (:cp, :cid)");
            params.put("cp", after.keyAsDouble());
            params.put("cid", after.id());
        }
        sql.append(" ORDER BY t.popularity DESC, t.id DESC LIMIT :limit");
        params.put("limit", size + 1);

        List<Row> rows = jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new Row(rs.getLong("id"), Double.toString(rs.getDouble("popularity"))))
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
                    FROM title t WHERE t.overview IS NOT NULL""");
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
