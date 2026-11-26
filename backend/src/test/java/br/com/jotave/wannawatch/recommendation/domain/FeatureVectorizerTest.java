package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureVectorizerTest {

    private final FeatureVectorizer vectorizer = new FeatureVectorizer(FeatureWeights.defaults());

    private Map<Long, SparseVector> vectors(TitleBuilder... titles) {
        return vectorizer.vectorize(java.util.Arrays.stream(titles).map(TitleBuilder::build).toList());
    }

    @Test
    void vetorFinalTemNormaUm() {
        var v = vectors(title(1).genres(1, 2).director(100).keywords(7), title(2).genres(1).director(100).keywords(7));
        assertThat(v.get(1L).norm()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void generoPrincipalPesaMaisQueOsOutros() {
        SparseVector v = vectors(title(1).genres(1, 2, 3)).get(1L);
        assertThat(v.get("g:1")).isGreaterThan(v.get("g:2"));
        assertThat(v.get("g:2")).isGreaterThan(v.get("g:3"));
        // 1 / (1 + 0.5·p): 1, 2/3, 1/2
        assertThat(v.get("g:2") / v.get("g:1")).isCloseTo(2.0 / 3, within(1e-9));
    }

    @Test
    void pessoaOuKeywordEmUmTituloSoEDescartada() {
        var v = vectors(title(1).genres(1).director(100).keywords(7), title(2).genres(2).director(300).keywords(7),
                title(3).genres(3).director(100));
        assertThat(v.get(1L).keys()).contains("p:100", "k:7");
        assertThat(v.get(2L).keys()).doesNotContain("p:300").contains("k:7");
    }

    @Test
    void keywordEmTodosOsTitulosNaoDiferenciaNinguem() {
        var v = vectors(title(1).genres(1).keywords(7), title(2).genres(2).keywords(7));
        assertThat(v.get(1L).keys()).doesNotContain("k:7"); // IDF = log(2/2) = 0
    }

    @Test
    void protagonistaPesaMaisQueCoadjuvante() {
        var v = vectors(title(1).cast(200, 0).cast(300, 8), title(2).cast(200, 0).cast(300, 8), title(3).genres(1));
        assertThat(v.get(1L).get("p:200")).isCloseTo(2 * v.get(1L).get("p:300"), within(1e-9));
    }

    @Test
    void mesmaDirecaoAproximaMaisQueSoOGenero() {
        var v = vectors(title(1).genres(1).director(100), title(2).genres(1).director(100), title(3).genres(1).director(300),
                title(4).genres(2).director(300));
        assertThat(v.get(1L).cosine(v.get(2L))).isGreaterThan(v.get(1L).cosine(v.get(3L)));
    }

    @Test
    void idiomaDecadaETipoViramFeatures() {
        SparseVector v = vectors(title(1).series().language("de").year(2017)).get(1L);
        assertThat(v.keys()).contains("lang:de", "dec:2010", "type:SERIES");
        SparseVector semAno = vectors(title(2).year(null)).get(2L);
        assertThat(semAno.keys()).noneMatch(k -> k.startsWith("dec:"));
        assertThat(List.copyOf(semAno.keys())).contains("type:MOVIE");
    }
}
