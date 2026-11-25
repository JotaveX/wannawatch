package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.util.Map;

/** Trechos de WHERE reaproveitados pelas listagens (tabela com alias "t"). */
public final class SqlFilters {

    private SqlFilters() {}

    public static String typeAndGenre(MediaType type, String genreSlug, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        if (type != null) {
            sql.append(" AND t.media_type = :type");
            params.put("type", type.name());
        }
        if (genreSlug != null && !genreSlug.isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM title_genre tg JOIN genre g ON g.id = tg.genre_id"
                    + " WHERE tg.title_id = t.id AND g.slug = :genre)");
            params.put("genre", genreSlug.trim().toLowerCase());
        }
        return sql.toString();
    }
}
