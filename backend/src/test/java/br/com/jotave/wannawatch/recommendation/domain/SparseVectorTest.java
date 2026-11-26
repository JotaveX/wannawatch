package br.com.jotave.wannawatch.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SparseVectorTest {

    @Test
    void cossenoDeVetoresIguaisEUmEDeOrtogonaisEZero() {
        SparseVector a = SparseVector.of(Map.of("x", 1.0, "y", 2.0));
        SparseVector b = SparseVector.of(Map.of("z", 3.0));
        assertThat(a.cosine(a.scaled(5))).isCloseTo(1.0, within(1e-9));
        assertThat(a.cosine(b)).isZero();
    }

    @Test
    void cossenoComVetorVazioEZero() {
        assertThat(SparseVector.EMPTY.cosine(SparseVector.of(Map.of("x", 1.0)))).isZero();
    }

    @Test
    void normalizarDeixaNormaUmENaoGuardaZeros() {
        SparseVector v = SparseVector.of(Map.of("x", 3.0, "y", 4.0, "z", 0.0));
        assertThat(v.keys()).containsExactlyInAnyOrder("x", "y");
        assertThat(v.normalized().norm()).isCloseTo(1.0, within(1e-9));
        assertThat(v.normalized().get("x")).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void builderSomaAsContribuicoes() {
        SparseVector v = SparseVector.builder().add("x", 1).add(SparseVector.of(Map.of("x", 2.0, "y", 1.0)), 0.5).build();
        assertThat(v.get("x")).isEqualTo(2.0);
        assertThat(v.get("y")).isEqualTo(0.5);
    }
}
