package br.com.jotave.wannawatch.recommendation.domain;

import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex.Entry;

/**
 * Score individual de um título para um usuário, em [0, 1]:
 *
 * <pre>
 * affinity = cos(positivo, t) − λ·cos(negativo, t), levada para [0, 1]
 * base     = 0.70·affinity + 0.15·pop + 0.15·quality
 * α        = min(1, sinaisPositivos / 5)
 * score    = α·base + (1 − α)·(0.5·pop + 0.3·quality + 0.2·gêneroFavorito)
 * </pre>
 */
public final class Scorer {

    private final ScoringWeights w;

    public Scorer(ScoringWeights weights) {
        this.w = weights;
    }

    public double affinity(TasteProfile profile, Entry title) {
        double raw = profile.positive().cosine(title.vector())
                - w.negativeFactor() * profile.negative().cosine(title.vector());
        // cossenos de vetores sem pesos negativos ficam em [0, 1]: raw vai de −λ a 1
        return clamp((raw + w.negativeFactor()) / (1 + w.negativeFactor()));
    }

    public double score(TasteProfile profile, Entry title) {
        double base = w.affinity() * affinity(profile, title) + w.popularity() * title.popularity()
                + w.quality() * title.quality();
        double alpha = w.coldStartSignals() <= 0 ? 1 : Math.min(1, (double) profile.positiveSignals() / w.coldStartSignals());
        if (alpha >= 1) return base;
        double favoriteGenre = profile.favoriteGenreId() != null && title.hasGenre(profile.favoriteGenreId()) ? 1 : 0;
        double cold = w.coldPopularity() * title.popularity() + w.coldQuality() * title.quality()
                + w.coldFavoriteGenre() * favoriteGenre;
        return alpha * base + (1 - alpha) * cold;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
