package br.com.jotave.wannawatch.recommendation.domain;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.util.List;

/**
 * Dados brutos de um título, como saem do banco, para montar o vetor e as notas de popularidade
 * e qualidade.
 *
 * @param genreIds        gêneros na ordem da TMDB (o principal primeiro)
 * @param recommendedIds  títulos do catálogo que a TMDB recomenda a partir deste
 */
public record TitleFeatures(
        long id,
        MediaType mediaType,
        String title,
        String originalLanguage,
        Integer year,
        List<Long> genreIds,
        List<Integer> keywordIds,
        List<Credit> credits,
        double popularity,
        Double voteAverage,
        Integer voteCount,
        List<Long> recommendedIds) {

    public TitleFeatures {
        genreIds = List.copyOf(genreIds);
        keywordIds = List.copyOf(keywordIds);
        credits = List.copyOf(credits);
        recommendedIds = List.copyOf(recommendedIds);
    }

    /** @param order posição no elenco (credit_order); para equipe é irrelevante */
    public record Credit(long personId, CreditRole role, int order) {}

    public Long primaryGenreId() {
        return genreIds.isEmpty() ? null : genreIds.getFirst();
    }
}
