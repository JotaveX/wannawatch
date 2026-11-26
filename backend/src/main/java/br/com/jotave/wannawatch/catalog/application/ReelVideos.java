package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.ReelVideo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Vídeos aprovados que o Reels pode tocar: aprovados na revisão, que aceitam embed e não estão
 * bloqueados na região. Com mais de um aprovado, vale o mais bem colocado na busca do YouTube.
 */
@Component
public class ReelVideos {

    /** Condição de "tocável" sobre {@code title_video v}. */
    static final String PLAYABLE = "v.status = 'APPROVED' AND v.embeddable AND NOT v.blocked_in_region";

    private final JdbcClient jdbc;

    public ReelVideos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Ids dos títulos com algum vídeo tocável (para o reforço na ordem do Reels). */
    public Set<Long> titlesWithVideo() {
        return new HashSet<>(jdbc.sql("SELECT DISTINCT v.title_id FROM title_video v WHERE " + PLAYABLE)
                .query(Long.class).list());
    }

    /** Um vídeo por título, só para os títulos que têm. */
    public Map<Long, ReelVideo> forTitles(List<Long> ids) {
        Map<Long, ReelVideo> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.sql("SELECT DISTINCT ON (v.title_id) v.title_id, v.platform, v.external_key"
                        + " FROM title_video v WHERE v.title_id IN (:ids) AND " + PLAYABLE
                        + " ORDER BY v.title_id, v.search_rank NULLS LAST, v.id")
                .param("ids", ids)
                .query((rs, n) -> out.put(rs.getLong("title_id"),
                        new ReelVideo(rs.getString("platform"), rs.getString("external_key"))))
                .list();
        return out;
    }
}
