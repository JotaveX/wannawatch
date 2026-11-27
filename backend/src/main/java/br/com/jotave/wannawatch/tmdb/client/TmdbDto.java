package br.com.jotave.wannawatch.tmdb.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Formato das respostas da TMDB que usamos. O JSON vem em snake_case e é convertido
 * pelo mapper do {@link TmdbClient}; só campos com nomes "estranhos" têm @JsonProperty.
 */
public final class TmdbDto {

    private TmdbDto() {}

    public record Page<T>(int page, List<T> results, int totalPages, int totalResults) {}

    /** Item das listas (popular, top_rated, trending). */
    public record ListItem(Integer id, String mediaType, Boolean adult, Double popularity) {}

    /** Item da busca (/search/multi): pode ser filme, série ou pessoa. */
    public record SearchItem(Integer id, String mediaType, Boolean adult, Double popularity, String posterPath) {}

    public record GenreList(List<Genre> genres) {}

    public record Genre(Integer id, String name) {}

    public record Company(Integer id, String name, String logoPath) {}

    public record Country(@JsonProperty("iso_3166_1") String code, String name) {}

    public record Person(Integer id, String name, String profilePath) {}

    public record MovieDetails(
            Integer id,
            String title,
            String originalTitle,
            String tagline,
            String overview,
            String originalLanguage,
            String releaseDate,
            Integer runtime,
            String status,
            String posterPath,
            String backdropPath,
            Double voteAverage,
            Integer voteCount,
            Double popularity,
            Long budget,
            Long revenue,
            Boolean adult,
            List<Genre> genres,
            List<Company> productionCompanies,
            List<Country> productionCountries,
            ReleaseDates releaseDates,
            Videos videos,
            Credits credits,
            Translations translations,
            @JsonProperty("watch/providers") WatchProviders watchProviders,
            Keywords keywords,
            Page<ListItem> recommendations) {}

    public record TvDetails(
            Integer id,
            String name,
            String originalName,
            String tagline,
            String overview,
            String originalLanguage,
            String firstAirDate,
            Integer numberOfSeasons,
            Integer numberOfEpisodes,
            List<Integer> episodeRunTime,
            String status,
            String posterPath,
            String backdropPath,
            Double voteAverage,
            Integer voteCount,
            Double popularity,
            Boolean adult,
            List<Genre> genres,
            List<Company> productionCompanies,
            List<Country> productionCountries,
            List<Company> networks,
            List<Person> createdBy,
            ContentRatings contentRatings,
            Videos videos,
            Credits credits,
            Translations translations,
            @JsonProperty("watch/providers") WatchProviders watchProviders,
            Keywords keywords,
            Page<ListItem> recommendations) {}

    /** Filmes mandam a lista em {@code keywords}; séries, em {@code results}. */
    public record Keywords(List<Keyword> keywords, List<Keyword> results) {

        public List<Keyword> all() {
            return keywords != null ? keywords : results != null ? results : List.of();
        }
    }

    public record Keyword(Integer id, String name) {}

    public record ReleaseDates(List<CountryReleaseDates> results) {}

    public record CountryReleaseDates(
            @JsonProperty("iso_3166_1") String country, List<ReleaseDate> releaseDates) {}

    public record ReleaseDate(String certification, Integer type) {}

    public record ContentRatings(List<ContentRating> results) {}

    public record ContentRating(@JsonProperty("iso_3166_1") String country, String rating) {}

    public record Videos(List<Video> results) {}

    public record Video(
            String key,
            String site,
            String type,
            Boolean official,
            @JsonProperty("iso_639_1") String language,
            String publishedAt) {}

    public record Credits(List<CastMember> cast, List<CrewMember> crew) {}

    public record CastMember(
            Integer id, String name, String profilePath, String character,
            Integer order, String knownForDepartment) {}

    public record CrewMember(
            Integer id, String name, String profilePath, String job,
            String department, String knownForDepartment) {}

    public record Translations(List<Translation> translations) {}

    public record Translation(
            @JsonProperty("iso_639_1") String language,
            @JsonProperty("iso_3166_1") String country,
            TranslationData data) {}

    public record TranslationData(String title, String name, String overview, String tagline) {}

    public record WatchProviders(Map<String, CountryProviders> results) {}

    public record CountryProviders(
            String link,
            List<Provider> flatrate,
            List<Provider> free,
            List<Provider> ads,
            List<Provider> rent,
            List<Provider> buy) {}

    public record Provider(String providerName, String logoPath, Integer displayPriority) {}
}
