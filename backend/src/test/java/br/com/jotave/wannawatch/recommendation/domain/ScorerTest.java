package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.index;
import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScorerTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private final Scorer scorer = new Scorer(ScoringWeights.defaults());

    // 1..6 ficção científica (pouco populares), 7 comédia muito popular, 8 terror
    private final TitleFeatureIndex idx = index(
            title(1).genres(1).popularity(5), title(2).genres(1).popularity(5), title(3).genres(1).popularity(5),
            title(4).genres(1).popularity(5), title(5).genres(1).popularity(5), title(6).genres(1).popularity(4),
            title(7).genres(3).popularity(900), title(8).genres(4).popularity(5));

    private TasteProfile likes(long... ids) {
        List<Interaction> list = new ArrayList<>();
        for (long id : ids) list.add(new Interaction(id, InteractionType.LIKE, NOW));
        return TasteProfile.build(list, null, idx, SignalWeights.defaults(), NOW);
    }

    private double score(TasteProfile p, long id) {
        return scorer.score(p, idx.get(id).orElseThrow());
    }

    @Test
    void semSinaisOMaisPopularGanha() {
        TasteProfile vazio = TasteProfile.EMPTY;
        assertThat(score(vazio, 7)).isGreaterThan(score(vazio, 6));
    }

    @Test
    void comSinaisSuficientesOGostoGanhaDaPopularidade() {
        TasteProfile fa = likes(1, 2, 3, 4, 5);
        assertThat(score(fa, 6)).isGreaterThan(score(fa, 7));
    }

    @Test
    void coldStartMisturaAosPoucos() {
        // com 1 sinal (α = 0.2) a popularidade ainda manda
        TasteProfile umSinal = likes(1);
        assertThat(score(umSinal, 7)).isGreaterThan(score(umSinal, 6));
    }

    @Test
    void generoFavoritoAjudaNoColdStart() {
        TasteProfile semFavorito = TasteProfile.EMPTY;
        TasteProfile comFavorito = TasteProfile.build(List.of(), 4L, idx, SignalWeights.defaults(), NOW);
        assertThat(score(comFavorito, 8)).isGreaterThan(score(semFavorito, 8));
    }

    @Test
    void perfilNegativoDerrubaAAfinidade() {
        TasteProfile p = TasteProfile.build(List.of(new Interaction(8, InteractionType.PASS, NOW)), null, idx,
                SignalWeights.defaults(), NOW);
        assertThat(scorer.affinity(p, idx.get(8).orElseThrow())).isLessThan(scorer.affinity(p, idx.get(7).orElseThrow()));
    }

    @Test
    void scoreEAfinidadeFicamEntreZeroEUm() {
        TasteProfile p = likes(1, 2, 3, 4, 5, 7);
        for (var e : idx.entries()) {
            assertThat(scorer.affinity(p, e)).isBetween(0.0, 1.0);
            assertThat(scorer.score(p, e)).isBetween(0.0, 1.0);
        }
    }

    @Test
    void percentilDoUsuarioVaiDoPiorAoMelhor() {
        UserScores s = UserScores.compute(idx, likes(1, 2, 3, 4, 5), scorer);
        assertThat(s.percentile(6)).isGreaterThan(s.percentile(8));
        assertThat(s.rank(List.of(8L, 6L, 7L)).getFirst()).isEqualTo(6L);
    }
}
