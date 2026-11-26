package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.index;
import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TitleFeatureIndexTest {

    @Test
    void topSimilarOrdenaDoMaisParecidoAoMenos() {
        var idx = index(title(1).genres(1).director(100), title(2).genres(1).director(100), title(3).genres(1, 2),
                title(4).genres(3), title(5).genres(2).director(300), title(6).genres(3).director(300));
        assertThat(idx.topSimilar(1, 2)).containsExactly(2L, 3L);
        assertThat(idx.topSimilar(1, 10)).hasSize(5).doesNotContain(1L);
    }

    @Test
    void topSimilarPulaOQueOPredicadoMandar() {
        var idx = index(title(1).genres(1), title(2).genres(1), title(3).genres(1));
        assertThat(idx.topSimilar(1, 5, id -> id == 2)).containsExactly(3L);
    }

    @Test
    void recomendacaoDaTmdbDaUmBonusNosDoisSentidos() {
        var idx = index(title(1).genres(1).recommends(3), title(2).genres(2), title(3).genres(2));
        assertThat(idx.similarity(1, 3) - idx.similarity(1, 2)).isCloseTo(0.1, within(1e-9));
        assertThat(idx.similarity(3, 1)).isEqualTo(idx.similarity(1, 3));
    }

    @Test
    void popularidadeViraPercentilNoCatalogo() {
        var idx = index(title(1).popularity(1), title(2).popularity(50), title(3).popularity(900));
        assertThat(idx.get(1).orElseThrow().popularity()).isZero();
        assertThat(idx.get(2).orElseThrow().popularity()).isEqualTo(0.5);
        assertThat(idx.get(3).orElseThrow().popularity()).isEqualTo(1.0);
    }

    @Test
    void notaAltaComPoucosVotosNaoPassaNotaBoaComMuitos() {
        var idx = index(title(1).votes(10, 3), title(2).votes(8.5, 20000), title(3).votes(6, 5000));
        double poucosVotos = idx.get(1).orElseThrow().quality();
        double muitosVotos = idx.get(2).orElseThrow().quality();
        assertThat(muitosVotos).isEqualTo(1.0);
        assertThat(poucosVotos).isLessThan(muitosVotos);
    }

    @Test
    void indiceVazioNaoQuebra() {
        var idx = TitleFeatureIndex.build(java.util.List.of(), FeatureWeights.defaults(), 0.1, 200,
                java.util.Map.of(), java.util.Map.of());
        assertThat(idx.isEmpty()).isTrue();
        assertThat(idx.topSimilar(1, 5)).isEmpty();
    }
}
