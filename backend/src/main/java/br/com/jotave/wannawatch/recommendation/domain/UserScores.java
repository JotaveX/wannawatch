package br.com.jotave.wannawatch.recommendation.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Score de todos os títulos do catálogo para um usuário, e o percentil de cada um. */
public final class UserScores {

    private final TitleFeatureIndex index;
    private final TasteProfile profile;
    private final Map<Long, Double> scores;
    private final Map<Long, Double> percentiles;

    private UserScores(TitleFeatureIndex index, TasteProfile profile, Map<Long, Double> scores) {
        this.index = index;
        this.profile = profile;
        this.scores = scores;
        this.percentiles = Percentiles.of(scores);
    }

    public static UserScores compute(TitleFeatureIndex index, TasteProfile profile, Scorer scorer) {
        Map<Long, Double> scores = new HashMap<>(index.size() * 2);
        for (TitleFeatureIndex.Entry e : index.entries()) scores.put(e.id(), scorer.score(profile, e));
        return new UserScores(index, profile, scores);
    }

    public TitleFeatureIndex index() {
        return index;
    }

    public TasteProfile profile() {
        return profile;
    }

    public double score(long titleId) {
        return scores.getOrDefault(titleId, 0.0);
    }

    /** Onde o título fica entre todos os do catálogo para esta pessoa (0 = o pior, 1 = o melhor). */
    public double percentile(long titleId) {
        return percentiles.getOrDefault(titleId, 0.0);
    }

    /** Os candidatos do maior para o menor score (desempate: popularidade, depois id). */
    public List<Long> rank(Collection<Long> candidates) {
        List<Long> out = new ArrayList<>(candidates);
        out.sort(Comparator.comparingDouble((Long id) -> -score(id))
                .thenComparingDouble(id -> -index.get(id).map(TitleFeatureIndex.Entry::popularity).orElse(0.0))
                .thenComparingLong(id -> id));
        return out;
    }
}
