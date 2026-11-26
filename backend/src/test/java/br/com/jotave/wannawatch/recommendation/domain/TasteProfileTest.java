package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.index;
import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TasteProfileTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private final TitleFeatureIndex idx = index(title(1).genres(1), title(2).genres(2), title(3).genres(3),
            title(4).genres(4));

    private TasteProfile profile(Long favoriteGenre, Interaction... interactions) {
        return TasteProfile.build(List.of(interactions), favoriteGenre, idx, SignalWeights.defaults(), NOW);
    }

    private static Interaction at(long titleId, InteractionType type, int daysAgo) {
        return new Interaction(titleId, type, NOW.minus(Duration.ofDays(daysAgo)));
    }

    @Test
    void decaimentoCaiPelaMetadeEmNoventaDias() {
        assertThat(TasteProfile.decay(NOW.minus(Duration.ofDays(90)), NOW, 90)).isCloseTo(0.5, within(1e-9));
        assertThat(TasteProfile.decay(NOW.minus(Duration.ofDays(180)), NOW, 90)).isCloseTo(0.25, within(1e-9));
        assertThat(TasteProfile.decay(NOW.plus(Duration.ofDays(1)), NOW, 90)).isEqualTo(1.0);
    }

    @Test
    void curtidaRecentePesaMaisQueAntiga() {
        TasteProfile p = profile(null, at(1, InteractionType.LIKE, 0), at(2, InteractionType.LIKE, 90));
        assertThat(p.genreWeight(1)).isCloseTo(2 * p.genreWeight(2), within(1e-9));
    }

    @Test
    void positivosENegativosVaoParaPerfisSeparados() {
        TasteProfile p = profile(null, at(1, InteractionType.LIKE, 0), at(2, InteractionType.PASS, 0),
                at(3, InteractionType.MATCH_NO, 0));
        assertThat(p.positive().get("g:1")).isPositive();
        assertThat(p.positive().get("g:2")).isZero();
        assertThat(p.negative().get("g:2")).isPositive();
        assertThat(p.negative().get("g:3")).isGreaterThan(p.negative().get("g:2")); // −0.5 pesa mais que −0.4
        assertThat(p.positiveSignals()).isEqualTo(1);
    }

    @Test
    void jaViSoExcluiMasCurtidaJuntoContinuaContando() {
        TasteProfile soVi = profile(null, at(1, InteractionType.SEEN, 0));
        assertThat(soVi.positive().isEmpty()).isTrue();
        assertThat(soVi.seen()).containsExactly(1L);

        TasteProfile viECurti = profile(null, at(1, InteractionType.SEEN, 0), at(1, InteractionType.LIKE, 0));
        assertThat(viECurti.genreWeight(1)).isPositive();
    }

    @Test
    void favoritosEntramSemDecaimento() {
        TasteProfile p = TasteProfile.build(List.of(new Interaction(1, InteractionType.FAVORITE_TITLE, NOW.minus(Duration.ofDays(900)))),
                4L, idx, SignalWeights.defaults(), NOW);
        assertThat(p.positiveSignals()).isEqualTo(1);
        assertThat(p.genreWeight(4)).isEqualTo(1.0);
        double semDecaimento = idx.get(1).orElseThrow().vector().get("g:1") * 1.5;
        assertThat(p.genreWeight(1)).isCloseTo(semDecaimento, within(1e-9));
    }

    @Test
    void ancorasSaoCurtidosESalvosDoMaisRecente() {
        TasteProfile p = profile(null, at(1, InteractionType.LIKE, 5), at(2, InteractionType.SAVED, 1),
                at(3, InteractionType.MAYBE, 0), at(4, InteractionType.MATCH_YES, 0));
        assertThat(p.anchors()).containsExactly(2L, 1L);
    }

    @Test
    void talvezSoExcluiDuranteOTempoDeEspera() {
        TasteProfile p = profile(null, at(1, InteractionType.MAYBE, 3), at(2, InteractionType.MAYBE, 8));
        var rules = EnumSet.of(Exclusion.RECENT_MAYBE);
        assertThat(p.excluded(1, rules, NOW, Duration.ofDays(7))).isTrue();
        assertThat(p.excluded(2, rules, NOW, Duration.ofDays(7))).isFalse();
    }

    @Test
    void ignoraTituloForaDoIndice() {
        TasteProfile p = profile(null, at(99, InteractionType.LIKE, 0));
        assertThat(p.positiveSignals()).isZero();
        assertThat(p.liked()).isEmpty();
    }
}
