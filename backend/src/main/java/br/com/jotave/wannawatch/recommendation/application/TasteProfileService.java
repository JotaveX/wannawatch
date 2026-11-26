package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.recommendation.domain.Interaction;
import br.com.jotave.wannawatch.recommendation.domain.InteractionType;
import br.com.jotave.wannawatch.recommendation.domain.TasteProfile;
import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o perfil de gosto de um usuário a partir de curtidas, sinais, votos de Match e favoritos.
 *
 * <p>Votos de Match: vale o último evento de cada (sala, título) no log de interações (os votos de
 * quem saiu da sala somem de match_vote, mas continuam dizendo algo do gosto). Votos anteriores ao
 * log só existem em match_vote e entram de lá.
 */
@Service
@Transactional(readOnly = true)
public class TasteProfileService {

    private final JdbcClient jdbc;
    private final RecommendationProperties props;
    private final Clock clock;

    public TasteProfileService(JdbcClient jdbc, RecommendationProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    public TasteProfile profile(long userId, TitleFeatureIndex index) {
        List<Interaction> interactions = new ArrayList<>(interactions(userId));
        record Favorites(Long title, Long genre) {}
        Favorites fav = jdbc.sql("SELECT favorite_title_id, favorite_genre_id FROM app_user WHERE id = :u")
                .param("u", userId)
                .query((rs, n) -> new Favorites((Long) rs.getObject("favorite_title_id", Long.class),
                        (Long) rs.getObject("favorite_genre_id", Long.class)))
                .optional().orElse(new Favorites(null, null));
        Instant now = clock.instant();
        if (fav.title() != null) interactions.add(new Interaction(fav.title(), InteractionType.FAVORITE_TITLE, now));
        return TasteProfile.build(interactions, fav.genre(), index, props.signals(), now);
    }

    List<Interaction> interactions(long userId) {
        return jdbc.sql("""
                SELECT title_id, 'LIKE' AS kind, created_at FROM title_like WHERE user_id = :u
                UNION ALL
                SELECT title_id, kind, created_at FROM user_title_signal WHERE user_id = :u
                UNION ALL
                SELECT title_id, kind, created_at FROM (
                    SELECT DISTINCT ON (e.room_id, e.title_external_id, e.media_type) t.id AS title_id, e.kind, e.created_at
                    FROM interaction_event e
                    JOIN title t ON t.external_id = e.title_external_id AND t.media_type = e.media_type
                    WHERE e.user_id = :u AND e.kind IN ('MATCH_VOTE_YES', 'MATCH_VOTE_NO', 'MATCH_UNVOTE')
                    ORDER BY e.room_id, e.title_external_id, e.media_type, e.id DESC
                ) last_vote WHERE kind <> 'MATCH_UNVOTE'
                UNION ALL
                SELECT v.title_id, CASE WHEN v.liked THEN 'MATCH_VOTE_YES' ELSE 'MATCH_VOTE_NO' END, v.created_at
                FROM match_vote v JOIN title t ON t.id = v.title_id
                WHERE v.user_id = :u AND NOT EXISTS (
                    SELECT 1 FROM interaction_event e
                    WHERE e.user_id = :u AND e.room_id = v.room_id AND e.kind LIKE 'MATCH_%'
                      AND e.title_external_id = t.external_id AND e.media_type = t.media_type)""")
                .param("u", userId)
                .query((rs, n) -> new Interaction(rs.getLong("title_id"), type(rs.getString("kind")),
                        instant(rs.getTimestamp("created_at"))))
                .list();
    }

    private static InteractionType type(String kind) {
        return switch (kind) {
            case "LIKE" -> InteractionType.LIKE;
            case "SAVED" -> InteractionType.SAVED;
            case "MAYBE" -> InteractionType.MAYBE;
            case "SEEN" -> InteractionType.SEEN;
            case "PASS" -> InteractionType.PASS;
            case "MATCH_VOTE_YES" -> InteractionType.MATCH_YES;
            case "MATCH_VOTE_NO" -> InteractionType.MATCH_NO;
            default -> throw new IllegalStateException("Interação desconhecida: " + kind);
        };
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
