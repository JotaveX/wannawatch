package br.com.jotave.wannawatch.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PercentilesTest {

    @Test
    void vaiDeZeroAUm() {
        assertThat(Percentiles.of(Map.of(1L, 0.1, 2L, 0.5, 3L, 0.9))).containsEntry(1L, 0.0).containsEntry(2L, 0.5)
                .containsEntry(3L, 1.0);
    }

    @Test
    void empateFicaComOPostoMedio() {
        // 1 abaixo + 3 empatados no topo: postos 1, 2 e 3 → média 2 → 2/3
        var p = Percentiles.of(Map.of(1L, 0.1, 2L, 0.9, 3L, 0.9, 4L, 0.9));
        assertThat(p.get(1L)).isZero();
        assertThat(p.get(2L)).isEqualTo(2.0 / 3);
        assertThat(p.get(3L)).isEqualTo(p.get(4L));
    }

    @Test
    void umSoValeUm() {
        assertThat(Percentiles.of(Map.of(7L, 0.3))).containsEntry(7L, 1.0);
    }
}
