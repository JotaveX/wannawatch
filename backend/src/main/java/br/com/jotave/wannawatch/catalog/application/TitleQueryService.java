package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.CastMember;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.Company;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.Country;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.CrewMember;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.GenreResponse;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.Provider;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleDetail;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.WatchProviders;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.catalog.domain.Title;
import br.com.jotave.wannawatch.catalog.domain.TitleDetails;
import br.com.jotave.wannawatch.catalog.domain.TitleRepository;
import br.com.jotave.wannawatch.recommendation.application.TitleFeatureIndexHolder;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import br.com.jotave.wannawatch.shared.web.PageResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TitleQueryService {

    static final int SIMILAR_LIMIT = 12;

    private final JdbcClient jdbc;
    private final TitleCards cards;
    private final TitleRepository titles;
    private final ImageUrls images;
    private final TitleFeatureIndexHolder index;

    public TitleQueryService(JdbcClient jdbc, TitleCards cards, TitleRepository titles, ImageUrls images,
                             TitleFeatureIndexHolder index) {
        this.index = index;
        this.jdbc = jdbc;
        this.cards = cards;
        this.titles = titles;
        this.images = images;
    }

    public List<GenreResponse> genres(MediaType type) {
        String where = type == null ? "" : (type == MediaType.MOVIE ? "WHERE g.for_movie" : "WHERE g.for_series");
        String typeJoin = type == null ? "" : " AND t.media_type = :type";
        Map<String, Object> params = new HashMap<>();
        if (type != null) params.put("type", type.name());
        return jdbc.sql("""
                SELECT g.id, g.name, g.slug, g.for_movie, g.for_series,
                       (SELECT count(*) FROM title_genre tg JOIN title t ON t.id = tg.title_id
                        WHERE tg.genre_id = g.id %s) AS title_count
                FROM genre g %s
                ORDER BY g.name""".formatted(typeJoin, where))
                .params(params)
                .query((rs, n) -> new GenreResponse(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getBoolean("for_movie"), rs.getBoolean("for_series"), rs.getLong("title_count")))
                .list();
    }

    public PageResponse<TitleCard> titles(MediaType type, String genre, TitleSort sort, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = "WHERE TRUE" + SqlFilters.typeAndGenre(type, genre, params);
        long total = jdbc.sql("SELECT count(*) FROM title t " + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<Long> ids = jdbc.sql("SELECT t.id FROM title t " + where
                        + " ORDER BY " + sort.orderBy + " LIMIT :limit OFFSET :offset")
                .params(params).query(Long.class).list();
        return PageResponse.of(cards.load(ids), page, size, total);
    }

    public TitleDetail detail(long id) {
        Title t = titles.findById(id).orElseThrow(() -> new NotFoundException("Título " + id + " não encontrado."));
        TitleCard card = cards.load(List.of(id)).getFirst();

        List<CastMember> cast = new ArrayList<>();
        List<CrewMember> crew = new ArrayList<>();
        jdbc.sql("""
                SELECT c.credit_type, c.character, c.job, p.id AS person_id, p.name, p.profile_path
                FROM title_credit c JOIN person p ON p.id = c.person_id
                WHERE c.title_id = :id
                ORDER BY c.credit_type, c.credit_order""")
                .param("id", id)
                .query((rs, n) -> {
                    if ("CAST".equals(rs.getString("credit_type"))) {
                        cast.add(new CastMember(rs.getLong("person_id"), rs.getString("name"),
                                rs.getString("character"), images.profile(rs.getString("profile_path"))));
                    } else {
                        crew.add(new CrewMember(rs.getLong("person_id"), rs.getString("name"),
                                Labels.job(rs.getString("job")), images.profile(rs.getString("profile_path"))));
                    }
                    return null;
                })
                .list();

        TitleDetails d = t.getDetails() == null ? TitleDetails.empty() : t.getDetails();
        return new TitleDetail(
                card,
                t.getOriginalTitle(),
                t.getTagline(),
                t.getReleaseDate(),
                Labels.status(t.getStatus()),
                t.getOriginalLanguage(),
                t.getEpisodesCount(),
                t.getVoteCount(),
                t.getBudget(),
                t.getRevenue(),
                trailerUrl(t.getTrailerYoutubeKey()),
                cast,
                crew,
                companies(d.productionCompanies()),
                d.productionCountries() == null ? List.of()
                        : d.productionCountries().stream().map(c -> new Country(c.code(), c.name())).toList(),
                companies(d.networks()),
                watchProviders(d.watchProviders()),
                cards.load(similarIds(id, t.getMediaType(), SIMILAR_LIMIT)));
    }

    /**
     * Os mais parecidos pelo índice de recomendação (gêneros, pessoas, keywords, idioma, época).
     * Se o título ainda não estiver no índice (acabou de chegar), cai na regra antiga: mais gêneros
     * em comum, preferindo o mesmo tipo e depois a popularidade.
     */
    public List<Long> similarIds(long id, MediaType type, int limit) {
        if (index.get().contains(id)) return index.get().topSimilar(id, limit);
        return jdbc.sql("""
                SELECT t.id
                FROM title t JOIN title_genre tg ON tg.title_id = t.id
                WHERE tg.genre_id IN (SELECT genre_id FROM title_genre WHERE title_id = :id)
                  AND t.id <> :id AND t.in_feeds
                GROUP BY t.id, t.media_type, t.popularity
                ORDER BY count(*) DESC, (t.media_type = :type) DESC, t.popularity DESC, t.id
                LIMIT :limit""")
                .param("id", id).param("type", type.name()).param("limit", limit)
                .query(Long.class).list();
    }

    static String trailerUrl(String youtubeKey) {
        return youtubeKey == null ? null : "https://www.youtube.com/watch?v=" + youtubeKey;
    }

    private List<Company> companies(List<TitleDetails.NamedLogo> list) {
        return list == null ? List.of()
                : list.stream().map(c -> new Company(c.name(), images.logo(c.logoPath()))).toList();
    }

    private WatchProviders watchProviders(TitleDetails.WatchProviders wp) {
        if (wp == null) wp = TitleDetails.WatchProviders.none();
        return new WatchProviders(wp.link(), providers(wp.streaming()), providers(wp.free()),
                providers(wp.ads()), providers(wp.rent()), providers(wp.buy()));
    }

    private List<Provider> providers(List<TitleDetails.NamedLogo> list) {
        return list == null ? List.of()
                : list.stream().map(p -> new Provider(p.name(), images.logo(p.logoPath()))).toList();
    }
}
