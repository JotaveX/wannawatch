package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeHero;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeResponse;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeRow;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.RowKind;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.recommendation.application.RecommendationService;
import br.com.jotave.wannawatch.recommendation.domain.Exclusion;
import br.com.jotave.wannawatch.recommendation.domain.Reason;
import br.com.jotave.wannawatch.recommendation.domain.TasteProfile;
import br.com.jotave.wannawatch.recommendation.domain.UserScores;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta a Home: destaque + fileiras do catálogo. A fileira "Seus amigos curtiram" depende de
 * quem está logado e sai de {@code GET /api/likes/friends}.
 *
 * <p>Com login, a Home é personalizada: destaque pelo score entre os 10 do trending, fileira
 * "Pra você", "Porque você curtiu X" com curtidas reais, o gênero mais forte do perfil entre as
 * fileiras de gênero e as fileiras de gênero e de séries ordenadas pelo score. Sem login fica como
 * sempre foi.
 */
@Service
@Transactional(readOnly = true)
public class HomeService {

    static final int ROW_SIZE = 15;
    static final int GENRE_ROWS = 3;
    static final int MIN_TITLES_PER_GENRE_ROW = 8;
    static final int FOR_YOU_SIZE = 20;
    static final int HERO_CANDIDATES = 10;
    /** "Porque você curtiu X" sai de um dos 3 curtidos/salvos mais recentes, no máximo 2 fileiras */
    static final int SIMILAR_ANCHORS = 3;
    static final int SIMILAR_ROWS = 2;
    static final Set<Exclusion> FOR_YOU_EXCLUSIONS =
            EnumSet.of(Exclusion.LIKED, Exclusion.SEEN, Exclusion.PASSED, Exclusion.SAVED);
    static final Set<Exclusion> SIMILAR_EXCLUSIONS = EnumSet.of(Exclusion.LIKED, Exclusion.SEEN);

    private final JdbcClient jdbc;
    private final TitleCards cards;
    private final TitleQueryService titles;
    private final Clock clock;
    private final RecommendationService recommendations;

    public HomeService(JdbcClient jdbc, TitleCards cards, TitleQueryService titles, Clock clock,
                       RecommendationService recommendations) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.titles = titles;
        this.clock = clock;
        this.recommendations = recommendations;
    }

    public HomeResponse home(Optional<AuthUser> user) {
        return user.map(u -> personalized(u.id())).orElseGet(this::home);
    }

    /** A Home de quem está logado. */
    HomeResponse personalized(long userId) {
        UserScores scores = recommendations.scores(userId);
        TasteProfile profile = scores.profile();
        Optional<Long> heroId = personalHeroId(scores).or(this::heroId);
        HomeHero hero = heroId.map(id -> hero(id, recommendations.reason(userId, id))).orElse(null);

        List<HomeRow> rows = new ArrayList<>();
        add(rows, "top10", RowKind.TOP10, "Top 10 de hoje no wannawatch?", ranking("trending_day", 10));

        List<Long> forYou = recommendations.rank(userId, withOverview(), FOR_YOU_EXCLUSIONS, true)
                .stream().limit(FOR_YOU_SIZE).toList();
        List<TitleCard> forYouCards = cards.load(forYou, recommendations.reasons(userId, forYou));
        if (!forYouCards.isEmpty()) rows.add(new HomeRow("for-you", RowKind.FOR_YOU, "Pra você", forYouCards));

        LongPredicate skipSimilar = recommendations.excluded(profile, SIMILAR_EXCLUSIONS);
        for (long anchor : similarAnchors(profile)) {
            String name = scores.index().get(anchor).map(e -> e.features().title()).orElse(null);
            if (name == null) continue;
            add(rows, "similar-" + anchor, RowKind.SIMILAR, "Porque você curtiu " + name,
                    recommendations.topSimilar(anchor, ROW_SIZE, id -> id == anchor || skipSimilar.test(id)));
        }

        add(rows, "binge", RowKind.BINGE, "Séries pra maratonar", scores.rank(bingeCandidates()).stream()
                .limit(ROW_SIZE).toList());
        for (GenreRow g : personalGenres(profile)) {
            add(rows, "genre-" + g.slug(), RowKind.GENRE, g.name(),
                    scores.rank(genreCandidates(g.id())).stream().limit(ROW_SIZE).toList());
        }
        add(rows, "top-rated", RowKind.TOP_RATED, "Filmes aclamados", ranking("top_movie", ROW_SIZE));
        return new HomeResponse(hero, rows);
    }

    /** Entre os 10 do trending com backdrop e sinopse, o de maior score. */
    private Optional<Long> personalHeroId(UserScores scores) {
        List<Long> candidates = jdbc.sql("""
                SELECT t.id FROM title_ranking r JOIN title t ON t.id = r.title_id
                WHERE r.list_key = 'trending_day' AND t.backdrop_path IS NOT NULL AND t.in_feeds AND t.overview IS NOT NULL
                ORDER BY r.position LIMIT :limit""")
                .param("limit", HERO_CANDIDATES).query(Long.class).list();
        return scores.rank(candidates).stream().findFirst();
    }

    /**
     * Até {@value #SIMILAR_ROWS} dos {@value #SIMILAR_ANCHORS} curtidos/salvos mais recentes,
     * começando por um que muda a cada dia. Sem curtidas, nenhuma fileira.
     */
    private List<Long> similarAnchors(TasteProfile profile) {
        List<Long> recent = profile.anchors().stream().limit(SIMILAR_ANCHORS).toList();
        if (recent.isEmpty()) return List.of();
        int start = (int) Math.floorMod(LocalDate.now(clock).toEpochDay(), (long) recent.size());
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < Math.min(SIMILAR_ROWS, recent.size()); i++) out.add(recent.get((start + i) % recent.size()));
        return out;
    }

    /** O gênero de maior peso no perfil (se tiver títulos suficientes) + os sorteados do dia. */
    private List<GenreRow> personalGenres(TasteProfile profile) {
        List<GenreRow> eligible = eligibleGenres();
        GenreRow top = eligible.stream().filter(g -> profile.genreWeight(g.id()) > 0)
                .max(Comparator.comparingDouble((GenreRow g) -> profile.genreWeight(g.id())).thenComparing(GenreRow::slug,
                        Comparator.reverseOrder()))
                .orElse(null);
        List<GenreRow> out = new ArrayList<>();
        if (top != null) out.add(top);
        for (GenreRow g : eligible) {
            if (out.size() == GENRE_ROWS) break;
            if (!g.equals(top)) out.add(g);
        }
        return out;
    }

    private List<Long> withOverview() {
        return jdbc.sql("SELECT id FROM title WHERE in_feeds AND overview IS NOT NULL").query(Long.class).list();
    }

    private List<Long> bingeCandidates() {
        return jdbc.sql("SELECT id FROM title WHERE media_type = :type AND seasons_count >= 2 AND in_feeds AND overview IS NOT NULL")
                .param("type", MediaType.SERIES.name()).query(Long.class).list();
    }

    private List<Long> genreCandidates(long genreId) {
        return jdbc.sql("""
                SELECT t.id FROM title t JOIN title_genre tg ON tg.title_id = t.id
                WHERE tg.genre_id = :g AND t.in_feeds AND t.overview IS NOT NULL""")
                .param("g", genreId).query(Long.class).list();
    }

    public HomeResponse home() {
        Optional<Long> heroId = heroId();
        HomeHero hero = heroId.map(this::hero).orElse(null);

        List<HomeRow> rows = new ArrayList<>();
        add(rows, "top10", RowKind.TOP10, "Top 10 de hoje no wannawatch?", ranking("trending_day", 10));
        heroId.ifPresent(id -> add(rows, "similar", RowKind.SIMILAR,
                "Porque você curtiu " + hero.card().title(),
                titles.similarIds(id, hero.card().mediaType(), ROW_SIZE)));
        add(rows, "binge", RowKind.BINGE, "Séries pra maratonar", bingeSeries());
        for (GenreRow g : genresOfTheDay()) {
            add(rows, "genre-" + g.slug(), RowKind.GENRE, g.name(), genreTitles(g.id()));
        }
        add(rows, "top-rated", RowKind.TOP_RATED, "Filmes aclamados", ranking("top_movie", ROW_SIZE));
        return new HomeResponse(hero, rows);
    }

    /** 1º do trending do dia com backdrop e sinopse; se não houver, o mais popular com essas duas coisas. */
    private Optional<Long> heroId() {
        return jdbc.sql("""
                SELECT t.id FROM title t
                LEFT JOIN title_ranking r ON r.title_id = t.id AND r.list_key = 'trending_day'
                WHERE t.backdrop_path IS NOT NULL AND t.in_feeds AND t.overview IS NOT NULL
                ORDER BY r.position NULLS LAST, t.popularity DESC
                LIMIT 1""").query(Long.class).optional();
    }

    private HomeHero hero(long id) {
        return hero(id, null);
    }

    private HomeHero hero(long id, Reason reason) {
        TitleCard card = cards.load(List.of(id)).getFirst().withReason(reason);
        record Extra(String tagline, String trailerKey) {}
        Extra extra = jdbc.sql("SELECT tagline, trailer_youtube_key FROM title WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Extra(rs.getString("tagline"), rs.getString("trailer_youtube_key")))
                .single();
        return new HomeHero(card, extra.tagline(), TitleQueryService.trailerUrl(extra.trailerKey()));
    }

    private List<Long> ranking(String listKey, int limit) {
        return jdbc.sql("""
                SELECT title_id FROM title_ranking WHERE list_key = :key ORDER BY position LIMIT :limit""")
                .param("key", listKey).param("limit", limit).query(Long.class).list();
    }

    private List<Long> bingeSeries() {
        return jdbc.sql("""
                SELECT id FROM title
                WHERE media_type = :type AND seasons_count >= 2 AND in_feeds AND overview IS NOT NULL
                ORDER BY popularity DESC, id LIMIT :limit""")
                .param("type", MediaType.SERIES.name()).param("limit", ROW_SIZE)
                .query(Long.class).list();
    }

    private record GenreRow(long id, String name, String slug) {}

    /** Gêneros com títulos suficientes, sorteados de forma estável ao longo do dia. */
    private List<GenreRow> genresOfTheDay() {
        return eligibleGenres().stream().limit(GENRE_ROWS).toList();
    }

    /** Todos os gêneros com títulos suficientes, na ordem do sorteio do dia. */
    private List<GenreRow> eligibleGenres() {
        String day = LocalDate.now(clock).toString();
        return jdbc.sql("""
                SELECT g.id, g.name, g.slug FROM genre g
                WHERE (SELECT count(*) FROM title_genre tg JOIN title t ON t.id = tg.title_id
                       WHERE tg.genre_id = g.id AND t.in_feeds AND t.overview IS NOT NULL) >= :min
                ORDER BY md5(:day || g.slug)""")
                .param("min", MIN_TITLES_PER_GENRE_ROW).param("day", day)
                .query((rs, n) -> new GenreRow(rs.getLong("id"), rs.getString("name"), rs.getString("slug")))
                .list();
    }

    private List<Long> genreTitles(long genreId) {
        return jdbc.sql("""
                SELECT t.id FROM title t JOIN title_genre tg ON tg.title_id = t.id
                WHERE tg.genre_id = :g AND t.in_feeds AND t.overview IS NOT NULL
                ORDER BY t.popularity DESC, t.id LIMIT :limit""")
                .param("g", genreId).param("limit", ROW_SIZE).query(Long.class).list();
    }

    private void add(List<HomeRow> rows, String key, RowKind kind, String title, List<Long> ids) {
        List<TitleCard> items = cards.load(ids);
        if (!items.isEmpty()) rows.add(new HomeRow(key, kind, title, items));
    }
}
