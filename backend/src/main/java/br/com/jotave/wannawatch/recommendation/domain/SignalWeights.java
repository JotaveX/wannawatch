package br.com.jotave.wannawatch.recommendation.domain;

/**
 * Quanto cada interação pesa no perfil de gosto (negativo = vai para o perfil negativo).
 * Todas decaem com meia-vida de {@code halfLifeDays}, menos o título favorito.
 */
public record SignalWeights(
        double favoriteTitle,
        double like,
        double saved,
        double matchYes,
        double maybe,
        double seen,
        double pass,
        double matchNo,
        double favoriteGenre,
        double halfLifeDays) {

    public static SignalWeights defaults() {
        return new SignalWeights(1.5, 1.0, 0.8, 0.6, 0.3, 0.0, -0.4, -0.5, 1.0, 90);
    }

    public double of(InteractionType type) {
        return switch (type) {
            case FAVORITE_TITLE -> favoriteTitle;
            case LIKE -> like;
            case SAVED -> saved;
            case MATCH_YES -> matchYes;
            case MAYBE -> maybe;
            case SEEN -> seen;
            case PASS -> pass;
            case MATCH_NO -> matchNo;
        };
    }
}
