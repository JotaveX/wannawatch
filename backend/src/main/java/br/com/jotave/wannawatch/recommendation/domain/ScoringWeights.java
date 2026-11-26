package br.com.jotave.wannawatch.recommendation.domain;

/**
 * Como afinidade, popularidade e qualidade viram o score individual.
 *
 * @param negativeFactor   quanto o perfil negativo desconta da afinidade
 * @param coldStartSignals com menos sinais positivos que isso, o score puxa para popularidade/qualidade
 */
public record ScoringWeights(
        double affinity,
        double popularity,
        double quality,
        double negativeFactor,
        int coldStartSignals,
        double coldPopularity,
        double coldQuality,
        double coldFavoriteGenre) {

    public static ScoringWeights defaults() {
        return new ScoringWeights(0.70, 0.15, 0.15, 0.5, 5, 0.5, 0.3, 0.2);
    }
}
