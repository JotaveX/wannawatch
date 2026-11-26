package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.feedback.SignalChangedEvent;
import br.com.jotave.wannawatch.recommendation.domain.DiversityReranker;
import br.com.jotave.wannawatch.recommendation.domain.Exclusion;
import br.com.jotave.wannawatch.recommendation.domain.Reason;
import br.com.jotave.wannawatch.recommendation.domain.ReasonResolver;
import br.com.jotave.wannawatch.recommendation.domain.Scorer;
import br.com.jotave.wannawatch.recommendation.domain.TasteProfile;
import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex;
import br.com.jotave.wannawatch.recommendation.domain.UserScores;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Score individual (e o percentil dele no catálogo) de cada título para um usuário, com cache.
 *
 * <p>O cache de um usuário cai em toda escrita de sinal, curtida ou voto dele (depois do commit),
 * quando o índice é reconstruído (o cache guarda a referência do índice com que foi calculado) e
 * depois de {@code cache-ttl}, para o "talvez" voltar sozinho aos feeds.
 */
@Service
public class RecommendationService {

    private final TitleFeatureIndexHolder indexHolder;
    private final TasteProfileService profiles;
    private final RecommendationProperties props;
    private final Clock clock;
    private final Scorer scorer;
    private final ReasonResolver reasons;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(UserScores scores, Instant at) {}

    public RecommendationService(TitleFeatureIndexHolder indexHolder, TasteProfileService profiles,
                                 RecommendationProperties props, Clock clock) {
        this.indexHolder = indexHolder;
        this.profiles = profiles;
        this.props = props;
        this.clock = clock;
        this.scorer = new Scorer(props.scoring());
        this.reasons = new ReasonResolver(props.reasonSimilarity());
    }

    public TitleFeatureIndex index() {
        return indexHolder.get();
    }

    public UserScores scores(long userId) {
        TitleFeatureIndex index = indexHolder.get();
        Instant now = clock.instant();
        Cached c = cache.get(userId);
        if (c != null && c.scores().index() == index && c.at().plus(props.cacheTtl()).isAfter(now)) return c.scores();
        TasteProfile profile = profiles.profile(userId, index);
        UserScores scores = UserScores.compute(index, profile, scorer);
        cache.put(userId, new Cached(scores, now));
        return scores;
    }

    public TasteProfile profile(long userId) {
        return scores(userId).profile();
    }

    /** Candidatos do maior para o menor score, sem os excluídos; com {@code diversify}, aplica a regra de gêneros no topo. */
    public List<Long> rank(long userId, Collection<Long> candidates, Set<Exclusion> exclusions, boolean diversify) {
        UserScores scores = scores(userId);
        LongPredicate excluded = excluded(scores.profile(), exclusions);
        List<Long> ranked = scores.rank(candidates.stream().filter(id -> !excluded.test(id)).toList());
        return diversify ? diversify(ranked, scores.index()) : ranked;
    }

    public List<Long> diversify(List<Long> ranked, TitleFeatureIndex index) {
        return DiversityReranker.rerank(ranked, id -> index.get(id).map(TitleFeatureIndex.Entry::primaryGenreId).orElse(null),
                props.diversity().window(), props.diversity().maxPerGenre());
    }

    public LongPredicate excluded(TasteProfile profile, Set<Exclusion> exclusions) {
        Instant now = clock.instant();
        return id -> profile.excluded(id, exclusions, now, props.maybeCooldown());
    }

    /** Motivo de um título para o usuário; nulo se o título não estiver no índice. */
    public Reason reason(long userId, long titleId) {
        UserScores scores = scores(userId);
        return scores.index().get(titleId).map(e -> reasons.resolve(scores.profile(), scores.index(), e)).orElse(null);
    }

    public Map<Long, Reason> reasons(long userId, Collection<Long> titleIds) {
        Map<Long, Reason> out = new java.util.HashMap<>();
        for (long id : titleIds) {
            Reason r = reason(userId, id);
            if (r != null) out.put(id, r);
        }
        return out;
    }

    /** Os {@code n} mais parecidos com o título (vazio se ele não estiver no índice). */
    public List<Long> topSimilar(long titleId, int n, LongPredicate skip) {
        return indexHolder.get().topSimilar(titleId, n, skip);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onSignalChanged(SignalChangedEvent event) {
        cache.remove(event.userId());
    }

    /** Para os testes, que mexem no banco por fora da API. */
    public void clearCache() {
        cache.clear();
    }
}
