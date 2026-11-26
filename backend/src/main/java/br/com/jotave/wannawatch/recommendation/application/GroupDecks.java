package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.recommendation.domain.GroupRanking;
import br.com.jotave.wannawatch.recommendation.domain.GroupReasonResolver;
import br.com.jotave.wannawatch.recommendation.domain.GroupReasonResolver.Member;
import br.com.jotave.wannawatch.recommendation.domain.Reason;
import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex;
import br.com.jotave.wannawatch.recommendation.domain.UserScores;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Deck do Match pelo gosto do grupo, e o motivo de cada carta para quem pede. */
@Service
public class GroupDecks {

    private final RecommendationService recommendations;
    private final RecommendationProperties props;
    private final GroupReasonResolver reasons;

    public GroupDecks(RecommendationService recommendations, RecommendationProperties props) {
        this.recommendations = recommendations;
        this.props = props;
        this.reasons = new GroupReasonResolver(props.group());
    }

    /** Participante: id e nome de exibição. */
    public record Participant(long userId, String displayName) {}

    public List<GroupRanking.Ranked> rank(List<Long> participantIds, List<Long> candidates, String seed) {
        List<UserScores> scores = participantIds.stream().map(recommendations::scores).toList();
        return GroupRanking.rank(candidates, scores, props.group(), seed);
    }

    /** Motivo de cada título para {@code me} (os sem motivo ficam de fora do mapa). */
    public Map<Long, Reason> reasons(long me, List<Participant> participants, Collection<Long> titleIds) {
        List<Member> members = participants.stream()
                .map(p -> new Member(p.userId(), p.displayName(), recommendations.scores(p.userId()))).toList();
        TitleFeatureIndex index = recommendations.index();
        Map<Long, Reason> out = new HashMap<>();
        for (long id : titleIds) {
            index.get(id).map(e -> reasons.resolve(me, members, e)).ifPresent(r -> out.put(id, r));
        }
        return out;
    }
}
