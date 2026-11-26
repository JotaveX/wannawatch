package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.recommendation.domain.FeatureWeights;
import br.com.jotave.wannawatch.recommendation.domain.ScoringWeights;
import br.com.jotave.wannawatch.recommendation.domain.SignalWeights;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pesos e limiares da recomendação ({@code wannawatch.recommendation.*} no application.yml).
 * Os valores padrão estão lá; aqui só caem os que faltarem.
 *
 * @param tmdbRecommendationBonus somado à similaridade quando a TMDB recomenda um título a partir do outro
 * @param qualityPriorVotes       "m" da média bayesiana da nota
 * @param reasonSimilarity        similaridade mínima para "Porque você curtiu X"
 * @param maybeCooldown           por quanto tempo um "talvez" fica fora dos feeds
 * @param cacheTtl                quanto tempo o score de um usuário fica em cache (escritas invalidam antes)
 */
@ConfigurationProperties("wannawatch.recommendation")
public record RecommendationProperties(
        FeatureWeights features,
        SignalWeights signals,
        ScoringWeights scoring,
        Double tmdbRecommendationBonus,
        Integer qualityPriorVotes,
        Double reasonSimilarity,
        Diversity diversity,
        Duration maybeCooldown,
        Duration cacheTtl) {

    public RecommendationProperties {
        if (features == null) features = FeatureWeights.defaults();
        if (signals == null) signals = SignalWeights.defaults();
        if (scoring == null) scoring = ScoringWeights.defaults();
        if (tmdbRecommendationBonus == null) tmdbRecommendationBonus = 0.1;
        if (qualityPriorVotes == null) qualityPriorVotes = 200;
        if (reasonSimilarity == null) reasonSimilarity = 0.35;
        if (diversity == null) diversity = new Diversity(10, 3);
        if (maybeCooldown == null) maybeCooldown = Duration.ofDays(7);
        if (cacheTtl == null) cacheTtl = Duration.ofMinutes(10);
    }

    /** No máximo {@code maxPerGenre} títulos com o mesmo gênero principal entre os {@code window} primeiros. */
    public record Diversity(int window, int maxPerGenre) {}
}
