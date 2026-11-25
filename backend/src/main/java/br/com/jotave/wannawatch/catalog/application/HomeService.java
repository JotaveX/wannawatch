package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeHero;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeResponse;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeRow;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.RowKind;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta a Home: destaque + fileiras do catálogo. A fileira "Seus amigos curtiram" depende de
 * quem está logado e sai de {@code GET /api/likes/friends}.
 */
@Service
@Transactional(readOnly = true)
public class HomeService {

    static final int ROW_SIZE = 15;
    static final int GENRE_ROWS = 3;
    static final int MIN_TITLES_PER_GENRE_ROW = 8;

    private final JdbcClient jdbc;
    private final TitleCards cards;
    private final TitleQueryService titles;
    private final Clock clock;

    public HomeService(JdbcClient jdbc, TitleCards cards, TitleQueryService titles, Clock clock) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.titles = titles;
        this.clock = clock;
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
                WHERE t.backdrop_path IS NOT NULL AND t.overview IS NOT NULL
                ORDER BY r.position NULLS LAST, t.popularity DESC
                LIMIT 1""").query(Long.class).optional();
    }

    private HomeHero hero(long id) {
        TitleCard card = cards.load(List.of(id)).getFirst();
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
                WHERE media_type = :type AND seasons_count >= 2 AND overview IS NOT NULL
                ORDER BY popularity DESC, id LIMIT :limit""")
                .param("type", MediaType.SERIES.name()).param("limit", ROW_SIZE)
                .query(Long.class).list();
    }

    private record GenreRow(long id, String name, String slug) {}

    /** Gêneros com títulos suficientes, sorteados de forma estável ao longo do dia. */
    private List<GenreRow> genresOfTheDay() {
        String day = LocalDate.now(clock).toString();
        return jdbc.sql("""
                SELECT g.id, g.name, g.slug FROM genre g
                WHERE (SELECT count(*) FROM title_genre tg JOIN title t ON t.id = tg.title_id
                       WHERE tg.genre_id = g.id AND t.overview IS NOT NULL) >= :min
                ORDER BY md5(:day || g.slug)
                LIMIT :limit""")
                .param("min", MIN_TITLES_PER_GENRE_ROW).param("day", day).param("limit", GENRE_ROWS)
                .query((rs, n) -> new GenreRow(rs.getLong("id"), rs.getString("name"), rs.getString("slug")))
                .list();
    }

    private List<Long> genreTitles(long genreId) {
        return jdbc.sql("""
                SELECT t.id FROM title t JOIN title_genre tg ON tg.title_id = t.id
                WHERE tg.genre_id = :g AND t.overview IS NOT NULL
                ORDER BY t.popularity DESC, t.id LIMIT :limit""")
                .param("g", genreId).param("limit", ROW_SIZE).query(Long.class).list();
    }

    private void add(List<HomeRow> rows, String key, RowKind kind, String title, List<Long> ids) {
        List<TitleCard> items = cards.load(ids);
        if (!items.isEmpty()) rows.add(new HomeRow(key, kind, title, items));
    }
}
