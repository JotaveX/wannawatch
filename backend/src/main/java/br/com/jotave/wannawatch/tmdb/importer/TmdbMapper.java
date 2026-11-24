package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.CreditType;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.catalog.domain.TitleDetails;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Converte as respostas da TMDB para {@link ImportedTitle}. Sem acesso a banco ou rede.
 *
 * <p>Regras de texto: usa o idioma pedido (pt-BR); se o título ou a sinopse vierem vazios,
 * cai para a tradução en-US. Sinopse que continuar vazia fica nula (o título sai do Swipe/Reels).
 * Tagline só no idioma pedido. Classificação indicativa e "onde assistir" só da região
 * configurada (BR); se não houver, ficam vazias.
 */
public class TmdbMapper {

    static final int MAX_CAST = 15;

    /** Funções da equipe que guardamos, na ordem em que aparecem na página de detalhes. */
    static final List<String> CREW_JOBS = List.of(
            "Creator", "Director", "Screenplay", "Writer", "Story", "Novel",
            "Original Music Composer", "Director of Photography");

    private final String region;
    private final String videoLanguage;

    /**
     * @param language idioma pedido à TMDB (ex.: "pt-BR"); define o idioma preferido do trailer.
     * @param region   país usado para classificação indicativa e "onde assistir" (ex.: "BR").
     */
    public TmdbMapper(String language, String region) {
        this.region = region;
        this.videoLanguage = language.split("-")[0];
    }

    public ImportedTitle fromMovie(TmdbDto.MovieDetails m) {
        Optional<TmdbDto.TranslationData> en = englishTranslation(m.translations());
        return new ImportedTitle(
                m.id(),
                MediaType.MOVIE,
                Boolean.TRUE.equals(m.adult()),
                firstText(m.title(), en.map(TmdbDto.TranslationData::title).orElse(null), m.originalTitle()),
                text(m.originalTitle()),
                text(m.tagline()),
                firstText(m.overview(), en.map(TmdbDto.TranslationData::overview).orElse(null)),
                text(m.originalLanguage()),
                date(m.releaseDate()),
                positive(m.runtime()),
                null,
                null,
                text(m.status()),
                text(m.posterPath()),
                text(m.backdropPath()),
                m.voteAverage(),
                m.voteCount(),
                m.popularity() == null ? 0 : m.popularity(),
                movieAgeRating(m.releaseDates()),
                positive(m.budget()),
                positive(m.revenue()),
                trailerKey(m.videos()),
                genreIds(m.genres()),
                credits(m.credits(), List.of()),
                new TitleDetails(
                        companies(m.productionCompanies()),
                        countries(m.productionCountries()),
                        List.of(),
                        watchProviders(m.watchProviders())));
    }

    public ImportedTitle fromTv(TmdbDto.TvDetails t) {
        Optional<TmdbDto.TranslationData> en = englishTranslation(t.translations());
        Integer episodeRuntime = t.episodeRunTime() == null ? null
                : t.episodeRunTime().stream().filter(Objects::nonNull).filter(v -> v > 0).findFirst().orElse(null);
        return new ImportedTitle(
                t.id(),
                MediaType.SERIES,
                Boolean.TRUE.equals(t.adult()),
                firstText(t.name(), en.map(TmdbDto.TranslationData::name).orElse(null), t.originalName()),
                text(t.originalName()),
                text(t.tagline()),
                firstText(t.overview(), en.map(TmdbDto.TranslationData::overview).orElse(null)),
                text(t.originalLanguage()),
                date(t.firstAirDate()),
                episodeRuntime,
                positive(t.numberOfSeasons()),
                positive(t.numberOfEpisodes()),
                text(t.status()),
                text(t.posterPath()),
                text(t.backdropPath()),
                t.voteAverage(),
                t.voteCount(),
                t.popularity() == null ? 0 : t.popularity(),
                tvAgeRating(t.contentRatings()),
                null,
                null,
                trailerKey(t.videos()),
                genreIds(t.genres()),
                credits(t.credits(), t.createdBy() == null ? List.of() : t.createdBy()),
                new TitleDetails(
                        companies(t.productionCompanies()),
                        countries(t.productionCountries()),
                        companies(t.networks()),
                        watchProviders(t.watchProviders())));
    }

    /** "Ficção científica" → "ficcao-cientifica"; "Action & Adventure" → "action-e-adventure". */
    public static String slug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("&", " e ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return base.isEmpty() ? "genero" : base;
    }

    // ---------------------------------------------------------------- classificação

    /** Filme: certificação BR, preferindo a de cinema (tipo 3), depois digital (4), depois qualquer uma. */
    String movieAgeRating(TmdbDto.ReleaseDates releaseDates) {
        if (releaseDates == null || releaseDates.results() == null) return null;
        return releaseDates.results().stream()
                .filter(r -> region.equalsIgnoreCase(r.country()))
                .flatMap(r -> nullSafe(r.releaseDates()).stream())
                .filter(d -> text(d.certification()) != null)
                .min(Comparator.comparingInt(d -> releaseTypePriority(d.type())))
                .map(d -> d.certification().trim())
                .orElse(null);
    }

    private static int releaseTypePriority(Integer type) {
        if (type == null) return 9;
        return switch (type) {
            case 3 -> 0;
            case 4 -> 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    String tvAgeRating(TmdbDto.ContentRatings ratings) {
        if (ratings == null || ratings.results() == null) return null;
        return ratings.results().stream()
                .filter(r -> region.equalsIgnoreCase(r.country()))
                .map(r -> text(r.rating()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------- trailer

    /** Trailer do YouTube: oficial primeiro, depois em pt, depois em en, depois o mais recente. */
    String trailerKey(TmdbDto.Videos videos) {
        if (videos == null || videos.results() == null) return null;
        Comparator<TmdbDto.Video> order = Comparator
                .comparing((TmdbDto.Video v) -> Boolean.TRUE.equals(v.official()) ? 0 : 1)
                .thenComparing(v -> languagePriority(v.language(), videoLanguage))
                .thenComparing(v -> Objects.requireNonNullElse(v.publishedAt(), ""), Comparator.reverseOrder());
        return videos.results().stream()
                .filter(v -> "YouTube".equalsIgnoreCase(v.site()))
                .filter(v -> "Trailer".equalsIgnoreCase(v.type()))
                .filter(v -> text(v.key()) != null)
                .min(order)
                .map(TmdbDto.Video::key)
                .orElse(null);
    }

    private static int languagePriority(String videoLang, String preferred) {
        if (preferred.equalsIgnoreCase(videoLang)) return 0;
        if ("en".equalsIgnoreCase(videoLang)) return 1;
        return 2;
    }

    // ---------------------------------------------------------------- créditos

    List<ImportedTitle.Credit> credits(TmdbDto.Credits credits, List<TmdbDto.Person> creators) {
        List<ImportedTitle.Credit> out = new ArrayList<>();
        if (credits != null && credits.cast() != null) {
            credits.cast().stream()
                    .filter(c -> c.id() != null && text(c.name()) != null)
                    .sorted(Comparator.comparing(c -> Objects.requireNonNullElse(c.order(), Integer.MAX_VALUE)))
                    .limit(MAX_CAST)
                    .forEach(c -> out.add(new ImportedTitle.Credit(
                            c.id(), c.name().trim(), text(c.profilePath()), text(c.knownForDepartment()),
                            CreditType.CAST, text(c.character()), null, null, out.size())));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<ImportedTitle.Credit> crew = new ArrayList<>();
        for (TmdbDto.Person p : creators) {
            if (p.id() != null && text(p.name()) != null && seen.add(p.id() + "|Creator")) {
                crew.add(new ImportedTitle.Credit(p.id(), p.name().trim(), text(p.profilePath()), "Creating",
                        CreditType.CREW, null, "Creator", "Creating", 0));
            }
        }
        if (credits != null && credits.crew() != null) {
            for (TmdbDto.CrewMember c : credits.crew()) {
                if (c.id() == null || text(c.name()) == null || !CREW_JOBS.contains(c.job())) continue;
                if (!seen.add(c.id() + "|" + c.job())) continue;
                crew.add(new ImportedTitle.Credit(c.id(), c.name().trim(), text(c.profilePath()),
                        text(c.knownForDepartment()), CreditType.CREW, null, c.job(), text(c.department()), 0));
            }
        }
        crew.sort(Comparator.comparingInt(c -> CREW_JOBS.indexOf(c.job())));
        for (int i = 0; i < crew.size(); i++) {
            ImportedTitle.Credit c = crew.get(i);
            out.add(new ImportedTitle.Credit(c.personExternalId(), c.name(), c.profilePath(),
                    c.knownForDepartment(), c.type(), c.character(), c.job(), c.department(), i));
        }
        return out;
    }

    // ---------------------------------------------------------------- detalhes de exibição

    TitleDetails.WatchProviders watchProviders(TmdbDto.WatchProviders wp) {
        if (wp == null || wp.results() == null) return TitleDetails.WatchProviders.none();
        TmdbDto.CountryProviders br = wp.results().entrySet().stream()
                .filter(e -> region.equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (br == null) return TitleDetails.WatchProviders.none();
        return new TitleDetails.WatchProviders(
                text(br.link()),
                providers(br.flatrate()),
                providers(br.free()),
                providers(br.ads()),
                providers(br.rent()),
                providers(br.buy()));
    }

    private static List<TitleDetails.NamedLogo> providers(List<TmdbDto.Provider> list) {
        return nullSafe(list).stream()
                .filter(p -> text(p.providerName()) != null)
                .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.displayPriority(), Integer.MAX_VALUE)))
                .map(p -> new TitleDetails.NamedLogo(p.providerName().trim(), text(p.logoPath())))
                .toList();
    }

    private static List<TitleDetails.NamedLogo> companies(List<TmdbDto.Company> list) {
        return nullSafe(list).stream()
                .filter(c -> text(c.name()) != null)
                .map(c -> new TitleDetails.NamedLogo(c.name().trim(), text(c.logoPath())))
                .toList();
    }

    private static List<TitleDetails.Country> countries(List<TmdbDto.Country> list) {
        return nullSafe(list).stream()
                .filter(c -> text(c.code()) != null)
                .map(c -> new TitleDetails.Country(c.code(), text(c.name())))
                .toList();
    }

    private static List<Integer> genreIds(List<TmdbDto.Genre> genres) {
        return nullSafe(genres).stream().map(TmdbDto.Genre::id).filter(Objects::nonNull).distinct().toList();
    }

    // ---------------------------------------------------------------- utilitários

    private static Optional<TmdbDto.TranslationData> englishTranslation(TmdbDto.Translations t) {
        if (t == null || t.translations() == null) return Optional.empty();
        Function<TmdbDto.Translation, Integer> rank = tr -> "US".equalsIgnoreCase(tr.country()) ? 0 : 1;
        return t.translations().stream()
                .filter(tr -> "en".equalsIgnoreCase(tr.language()) && tr.data() != null)
                .min(Comparator.comparing(rank))
                .map(TmdbDto.Translation::data);
    }

    static String text(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstText(String... options) {
        return Stream.of(options).map(TmdbMapper::text).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static LocalDate date(String s) {
        String t = text(s);
        if (t == null) return null;
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer positive(Integer v) {
        return v == null || v <= 0 ? null : v;
    }

    private static Long positive(Long v) {
        return v == null || v <= 0 ? null : v;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
