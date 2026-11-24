package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.MatchKind;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.MatchedBy;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.SearchHit;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.shared.web.PageResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca fuzzy (pg_trgm + unaccent) em títulos, pessoas e gêneros, devolvendo uma lista única
 * de títulos. Cada título aparece uma vez, pelo melhor motivo: nome do título > pessoa > gênero.
 * Usa word_similarity, então acha pedaços do que está sendo digitado ("vinga") e erros ("vingadres").
 *
 * <p>Ordem: primeiro a qualidade do acerto (em faixas de 0,1), e dentro da mesma faixa
 * título > pessoa > gênero, depois popularidade. Assim um gênero exato ("terror") vem antes
 * de um título só parecido ("Terra da Máfia").
 */
@Service
public class SearchService {

    private static final String SEARCH_CTE = """
            WITH q AS (SELECT f_unaccent(lower(:q)) AS q),
            by_title AS (
                SELECT t.id, 1 AS kind_rank, NULL::text AS label, NULL::text AS credit_type, NULL::text AS job,
                       greatest(word_similarity(q.q, f_unaccent(lower(t.title))),
                                word_similarity(q.q, f_unaccent(lower(coalesce(t.original_title, ''))))) AS score
                FROM title t, q
                WHERE q.q <% f_unaccent(lower(t.title))
                   OR q.q <% f_unaccent(lower(coalesce(t.original_title, '')))
            ),
            by_person AS (
                SELECT DISTINCT ON (c.title_id) c.title_id AS id, 2 AS kind_rank,
                       p.name::text AS label, c.credit_type::text, c.job::text,
                       word_similarity(q.q, f_unaccent(lower(p.name))) AS score
                FROM person p JOIN title_credit c ON c.person_id = p.id, q
                WHERE q.q <% f_unaccent(lower(p.name))
                ORDER BY c.title_id, score DESC, c.credit_type, c.credit_order
            ),
            by_genre AS (
                SELECT DISTINCT ON (tg.title_id) tg.title_id AS id, 3 AS kind_rank, g.name::text AS label,
                       NULL::text AS credit_type, NULL::text AS job,
                       word_similarity(q.q, f_unaccent(lower(g.name))) AS score
                FROM genre g JOIN title_genre tg ON tg.genre_id = g.id, q
                WHERE q.q <% f_unaccent(lower(g.name))
                ORDER BY tg.title_id, score DESC
            ),
            best AS (
                SELECT DISTINCT ON (id) id, kind_rank, label, credit_type, job, score
                FROM (SELECT * FROM by_title UNION ALL SELECT * FROM by_person UNION ALL SELECT * FROM by_genre) a
                ORDER BY id, kind_rank, score DESC
            )
            """;

    private final JdbcClient jdbc;
    private final TitleCards cards;
    private final double threshold;

    public SearchService(JdbcClient jdbc, TitleCards cards,
                         @Value("${wannawatch.search.similarity-threshold:0.6}") double threshold) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.threshold = threshold;
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchHit> search(String query, int page, int size) {
        String q = query.trim();
        // Vale só para esta transação.
        jdbc.sql("SELECT set_config('pg_trgm.word_similarity_threshold', :t, true)")
                .param("t", Double.toString(threshold)).query(String.class).single();

        long total = jdbc.sql(SEARCH_CTE + "SELECT count(*) FROM best").param("q", q).query(Long.class).single();

        Map<Long, MatchedBy> reasons = new HashMap<>();
        List<Long> ids = jdbc.sql(SEARCH_CTE + """
                SELECT best.id, best.kind_rank, best.label, best.credit_type, best.job
                FROM best JOIN title t ON t.id = best.id
                ORDER BY round(best.score::numeric, 1) DESC, best.kind_rank, t.popularity DESC, t.id
                LIMIT :limit OFFSET :offset""")
                .param("q", q).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> {
                    long id = rs.getLong("id");
                    reasons.put(id, matchedBy(rs.getInt("kind_rank"), rs.getString("label"),
                            rs.getString("credit_type"), rs.getString("job")));
                    return id;
                })
                .list();

        List<TitleCard> found = cards.load(ids);
        List<SearchHit> hits = found.stream().map(c -> new SearchHit(c, reasons.get(c.id()))).toList();
        return PageResponse.of(hits, page, size, total);
    }

    private static MatchedBy matchedBy(int kindRank, String label, String creditType, String job) {
        return switch (kindRank) {
            case 1 -> new MatchedBy(MatchKind.TITLE, null);
            case 2 -> new MatchedBy(MatchKind.PERSON, personLabel(label, creditType, job));
            default -> new MatchedBy(MatchKind.GENRE, label);
        };
    }

    /** "com Fulana" para elenco; "direção de Fulano", "roteiro de Fulana"... para equipe. */
    private static String personLabel(String name, String creditType, String job) {
        if ("CAST".equals(creditType) || job == null) return "com " + name;
        return Labels.job(job).toLowerCase() + " de " + name;
    }
}
