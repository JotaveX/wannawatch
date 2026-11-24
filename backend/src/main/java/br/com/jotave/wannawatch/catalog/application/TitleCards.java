package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.GenreRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Carrega {@link TitleCard}s a partir de uma lista ordenada de ids, em duas consultas
 * (títulos + gêneros), preservando a ordem recebida. As consultas de listagem só
 * descobrem os ids; quem monta o card é sempre esta classe.
 */
@Component
public class TitleCards {

    private final JdbcClient jdbc;
    private final ImageUrls images;

    public TitleCards(JdbcClient jdbc, ImageUrls images) {
        this.jdbc = jdbc;
        this.images = images;
    }

    public List<TitleCard> load(List<Long> ids) {
        if (ids.isEmpty()) return List.of();

        Map<Long, List<GenreRef>> genres = new HashMap<>();
        jdbc.sql("""
                SELECT tg.title_id, g.id, g.name, g.slug
                FROM title_genre tg JOIN genre g ON g.id = tg.genre_id
                WHERE tg.title_id IN (:ids)
                ORDER BY tg.title_id, tg.position""")
                .param("ids", ids)
                .query((rs, n) -> {
                    genres.computeIfAbsent(rs.getLong("title_id"), k -> new ArrayList<>())
                            .add(new GenreRef(rs.getLong("id"), rs.getString("name"), rs.getString("slug")));
                    return null;
                })
                .list();

        Map<Long, TitleCard> byId = new HashMap<>();
        jdbc.sql("""
                SELECT id, media_type, title, release_date, overview, runtime_minutes, seasons_count,
                       age_rating, vote_average, poster_path, backdrop_path
                FROM title WHERE id IN (:ids)""")
                .param("ids", ids)
                .query((rs, n) -> {
                    long id = rs.getLong("id");
                    Date release = rs.getDate("release_date");
                    byId.put(id, new TitleCard(
                            id,
                            MediaType.valueOf(rs.getString("media_type")),
                            rs.getString("title"),
                            release == null ? null : release.toLocalDate().getYear(),
                            genres.getOrDefault(id, List.of()),
                            rs.getString("overview"),
                            (Integer) rs.getObject("runtime_minutes"),
                            (Integer) rs.getObject("seasons_count"),
                            rs.getString("age_rating"),
                            (Double) rs.getObject("vote_average"),
                            images.poster(rs.getString("poster_path")),
                            images.backdrop(rs.getString("backdrop_path"))));
                    return null;
                })
                .list();

        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
