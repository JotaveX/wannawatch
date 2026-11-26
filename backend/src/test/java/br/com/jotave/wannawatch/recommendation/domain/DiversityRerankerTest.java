package br.com.jotave.wannawatch.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiversityRerankerTest {

    @Test
    void noMaximoTresDoMesmoGeneroEntreOsDezPrimeiros() {
        // 1..8 = gênero A; 9..12 = gênero B
        Map<Long, Long> genre = new java.util.HashMap<>();
        for (long i = 1; i <= 8; i++) genre.put(i, 100L);
        for (long i = 9; i <= 12; i++) genre.put(i, 200L);
        List<Long> ranked = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);

        List<Long> out = DiversityReranker.rerank(ranked, genre::get, 10, 3);

        assertThat(out).hasSize(12).containsExactlyInAnyOrderElementsOf(ranked);
        assertThat(out.subList(0, 4)).containsExactly(1L, 2L, 3L, 9L);
        // gênero A e B esgotam o limite: o resto do topo segue a ordem original
        assertThat(out.subList(0, 10).stream().filter(id -> genre.get(id) == 100L).count()).isGreaterThan(3);
        assertThat(out.subList(0, 6)).containsExactly(1L, 2L, 3L, 9L, 10L, 11L);
    }

    @Test
    void comGenerosVariadosNaoMexeEmNada() {
        List<Long> ranked = List.of(1L, 2L, 3L, 4L);
        assertThat(DiversityReranker.rerank(ranked, id -> id, 10, 3)).isEqualTo(ranked);
    }

    @Test
    void semGeneroNaoContaNoLimite() {
        List<Long> ranked = List.of(1L, 2L, 3L, 4L, 5L);
        assertThat(DiversityReranker.rerank(ranked, id -> null, 10, 1)).isEqualTo(ranked);
    }
}
