package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.recommendation.domain.FeatureWeights;
import br.com.jotave.wannawatch.recommendation.domain.GroupParams;
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
 * @param feed                    Swipe e Reels
 * @param group                   deck do Match
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
        Duration cacheTtl,
        Feed feed,
        GroupParams group) {

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
        if (feed == null) feed = new Feed(0.15, 5, 0.25, Duration.ofMinutes(30), 0.6, 0.4);
        if (group == null) group = GroupParams.defaults();
    }

    /** No máximo {@code maxPerGenre} títulos com o mesmo gênero principal entre os {@code window} primeiros. */
    public record Diversity(int window, int maxPerGenre) {}

    /**
     * @param swipeNoise            ordem do Swipe = score + swipeNoise·ruído(seed, id), ruído em [0, 1)
     * @param explorationEvery      a cada N cartas do Swipe, 1 é de exploração (0 desliga)
     * @param explorationGenreShare gênero "com pouco peso" = peso até essa fração do gênero mais forte
     * @param snapshotTtl           quanto tempo a ordem de uma visita fica guardada para a paginação
     * @param reelsScore            ordem do Reels = reelsScore·score + reelsPopularity·popularidade
     */
    public record Feed(double swipeNoise, int explorationEvery, double explorationGenreShare, Duration snapshotTtl,
                       double reelsScore, double reelsPopularity) {}
}
