package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.recommendation.domain.CreditRole;
import br.com.jotave.wannawatch.recommendation.domain.TitleFeatures;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê do banco tudo o que o índice de recomendação precisa, em poucas consultas. Só entram os
 * títulos dos feeds ({@code in_feeds}); os que existem só para a busca ficam de fora.
 */
@Component
public class CatalogFeatureLoader {

    private final JdbcClient jdbc;

    public CatalogFeatureLoader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record CatalogData(List<TitleFeatures> titles, Map<Long, String> genreNames, Map<Long, String> personNames) {}

    @Transactional(readOnly = true)
    public CatalogData load() {
        Map<Long, List<Long>> genres = new HashMap<>();
        jdbc.sql("""
                SELECT tg.title_id, tg.genre_id FROM title_genre tg JOIN title t ON t.id = tg.title_id
                WHERE t.in_feeds ORDER BY tg.title_id, tg.position, tg.genre_id""")
                .query((rs, n) -> genres.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)))
                .list();

        Map<Long, List<Integer>> keywords = new HashMap<>();
        jdbc.sql("SELECT k.title_id, k.keyword_id FROM title_keyword k JOIN title t ON t.id = k.title_id WHERE t.in_feeds")
                .query((rs, n) -> keywords.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getInt(2)))
                .list();

        Map<Long, List<TitleFeatures.Credit>> credits = new HashMap<>();
        jdbc.sql("""
                SELECT c.title_id, c.person_id, c.credit_type, c.job, c.credit_order
                FROM title_credit c JOIN title t ON t.id = c.title_id WHERE t.in_feeds""")
                .query((rs, n) -> {
                    CreditRole role = CreditRole.of(rs.getString("credit_type"), rs.getString("job"));
                    if (role != null) {
                        credits.computeIfAbsent(rs.getLong("title_id"), k -> new ArrayList<>())
                                .add(new TitleFeatures.Credit(rs.getLong("person_id"), role, rs.getInt("credit_order")));
                    }
                    return null;
                })
                .list();

        // só as recomendações que estão no catálogo
        Map<Long, List<Long>> recommended = new HashMap<>();
        jdbc.sql("""
                SELECT r.title_id, t.id FROM title_tmdb_recommendation r
                JOIN title t ON t.external_id = r.external_id AND t.media_type = r.media_type
                JOIN title src ON src.id = r.title_id
                WHERE t.id <> r.title_id AND t.in_feeds AND src.in_feeds""")
                .query((rs, n) -> recommended.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)))
                .list();

        List<TitleFeatures> titles = jdbc.sql("""
                SELECT id, media_type, title, original_language, release_date, popularity, vote_average, vote_count
                FROM title WHERE in_feeds""")
                .query((rs, n) -> {
                    long id = rs.getLong("id");
                    Date release = rs.getDate("release_date");
                    return new TitleFeatures(id, MediaType.valueOf(rs.getString("media_type")), rs.getString("title"),
                            rs.getString("original_language"), release == null ? null : release.toLocalDate().getYear(),
                            genres.getOrDefault(id, List.of()), keywords.getOrDefault(id, List.of()),
                            credits.getOrDefault(id, List.of()), rs.getDouble("popularity"),
                            (Double) rs.getObject("vote_average"), (Integer) rs.getObject("vote_count"),
                            recommended.getOrDefault(id, List.of()));
                })
                .list();

        Map<Long, String> genreNames = new HashMap<>();
        jdbc.sql("SELECT id, name FROM genre").query((rs, n) -> genreNames.put(rs.getLong(1), rs.getString(2))).list();
        Map<Long, String> personNames = new HashMap<>();
        jdbc.sql("""
                SELECT DISTINCT p.id, p.name FROM person p JOIN title_credit c ON c.person_id = p.id
                JOIN title t ON t.id = c.title_id WHERE t.in_feeds""")
                .query((rs, n) -> personNames.put(rs.getLong(1), rs.getString(2))).list();
        return new CatalogData(titles, genreNames, personNames);
    }
}
