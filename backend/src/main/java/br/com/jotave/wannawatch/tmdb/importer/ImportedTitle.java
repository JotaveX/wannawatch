package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.CreditType;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.catalog.domain.TitleDetails;
import java.time.LocalDate;
import java.util.List;

/** Título já traduzido do formato da TMDB para o nosso, pronto para ser gravado. */
public record ImportedTitle(
        int externalId,
        MediaType mediaType,
        boolean adult,
        String title,
        String originalTitle,
        String tagline,
        String overview,
        String originalLanguage,
        LocalDate releaseDate,
        Integer runtimeMinutes,
        Integer seasonsCount,
        Integer episodesCount,
        String status,
        String posterPath,
        String backdropPath,
        Double voteAverage,
        Integer voteCount,
        double popularity,
        String ageRating,
        Long budget,
        Long revenue,
        String trailerYoutubeKey,
        List<Integer> genreExternalIds,
        List<Credit> credits,
        TitleDetails details,
        List<Keyword> keywords,
        List<Recommendation> recommendations) {

    public record Keyword(int id, String name) {}

    /** Título que a TMDB recomenda, na ordem dela (pode não estar no catálogo). */
    public record Recommendation(int externalId, MediaType mediaType) {}

    public record Credit(
            int personExternalId,
            String name,
            String profilePath,
            String knownForDepartment,
            CreditType type,
            String character,
            String job,
            String department,
            int order) {}
}
