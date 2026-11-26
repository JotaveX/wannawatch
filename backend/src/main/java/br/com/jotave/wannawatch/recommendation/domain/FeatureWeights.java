package br.com.jotave.wannawatch.recommendation.domain;

/**
 * Peso de cada família de features no vetor do título (cada família é normalizada antes).
 *
 * @param genrePositionDecay  gênero na posição p vale 1 / (1 + decay·p)
 * @param castTopBilling      quem está antes desta posição no elenco conta como protagonista
 * @param minDocumentFrequency pessoa ou keyword em menos títulos que isso é descartada
 */
public record FeatureWeights(
        double genre,
        double keyword,
        double direction,
        double writing,
        double cast,
        double otherCrew,
        double language,
        double decade,
        double type,
        double genrePositionDecay,
        int castTopBilling,
        double castTopWeight,
        double castRestWeight,
        int minDocumentFrequency) {

    public static FeatureWeights defaults() {
        return new FeatureWeights(3.0, 2.0, 2.0, 1.0, 1.0, 0.3, 0.5, 0.3, 0.3, 0.5, 5, 1.0, 0.5, 2);
    }
}
