package br.com.jotave.wannawatch.catalog.api;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.recommendation.domain.Reason;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.util.List;

/** Contratos JSON da API de catálogo. O front depende só destes formatos. */
public final class CatalogDtos {

    private CatalogDtos() {}

    public record GenreRef(long id, String name, String slug) {}

    public record GenreResponse(long id, String name, String slug, boolean forMovie, boolean forSeries,
                                long titleCount) {}

    /** URL completa da imagem em dois tamanhos (para srcset). */
    public record ImageSet(String small, String large) {}

    /**
     * Formato usado em todas as listas (Home, Swipe, Reels, busca, parecidos). {@code reason} só vem
     * nas listas personalizadas (por que o título apareceu para quem está logado); nas outras é nulo.
     */
    public record TitleCard(
            long id,
            MediaType mediaType,
            String title,
            Integer year,
            List<GenreRef> genres,
            String overview,
            Integer runtimeMinutes,
            Integer seasonsCount,
            String ageRating,
            Double voteAverage,
            ImageSet poster,
            ImageSet backdrop,
            Reason reason) {

        public TitleCard withReason(Reason reason) {
            return new TitleCard(id, mediaType, title, year, genres, overview, runtimeMinutes, seasonsCount,
                    ageRating, voteAverage, poster, backdrop, reason);
        }
    }

    /** Vídeo curto aprovado de um título (toca pelo embed do YouTube; {@code key} é o id do vídeo). */
    public record ReelVideo(String platform, String key) {}

    /** Item do Reels: o card de sempre e, quando o título tem vídeo aprovado, o vídeo (senão nulo). */
    public record ReelCard(@JsonUnwrapped TitleCard card, ReelVideo video) {}

    public record TitleDetail(
            @JsonUnwrapped TitleCard card,
            String originalTitle,
            String tagline,
            LocalDate releaseDate,
            String status,
            String originalLanguage,
            Integer episodesCount,
            Integer voteCount,
            Long budget,
            Long revenue,
            String trailerUrl,
            List<CastMember> cast,
            List<CrewMember> crew,
            List<Company> productionCompanies,
            List<Country> productionCountries,
            List<Company> networks,
            WatchProviders watchProviders,
            List<TitleCard> similar) {}

    public record CastMember(long personId, String name, String character, String photoUrl) {}

    /** {@code job} já vem traduzido: Direção, Roteiro, Criação… */
    public record CrewMember(long personId, String name, String job, String photoUrl) {}

    public record Company(String name, String logoUrl) {}

    public record Country(String code, String name) {}

    /** Onde assistir no Brasil. {@code link} leva à página da TMDB/JustWatch com os links de cada serviço. */
    public record WatchProviders(
            String link,
            List<Provider> streaming,
            List<Provider> free,
            List<Provider> ads,
            List<Provider> rent,
            List<Provider> buy) {}

    public record Provider(String name, String logoUrl) {}

    public record HomeResponse(HomeHero hero, List<HomeRow> rows) {}

    public record HomeHero(@JsonUnwrapped TitleCard card, String tagline, String trailerUrl) {}

    public enum RowKind { TOP10, FOR_YOU, SIMILAR, BINGE, GENRE, TOP_RATED }

    public record HomeRow(String key, RowKind kind, String title, List<TitleCard> items) {}

    public enum MatchKind { TITLE, PERSON, GENRE }

    /** Por que o título apareceu na busca. {@code label} é nulo quando bateu pelo nome do título. */
    public record MatchedBy(MatchKind kind, String label) {}

    public record SearchHit(@JsonUnwrapped TitleCard card, MatchedBy matchedBy) {}
}
