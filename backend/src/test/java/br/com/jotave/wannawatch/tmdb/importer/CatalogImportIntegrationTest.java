package br.com.jotave.wannawatch.tmdb.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import br.com.jotave.wannawatch.tmdb.TmdbFixtures;
import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import br.com.jotave.wannawatch.tmdb.client.TmdbException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Importação ponta a ponta contra um Postgres real, com a TMDB simulada pelas fixtures. */
@SpringBootTest
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class CatalogImportIntegrationTest {

    @MockitoBean
    TmdbClient tmdb;

    @Autowired
    CatalogImporter importer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE title, genre, person, keyword RESTART IDENTITY CASCADE").update();

        when(tmdb.movieGenres()).thenReturn(TmdbFixtures.read("genres-movie.json", TmdbDto.GenreList.class));
        when(tmdb.tvGenres()).thenReturn(TmdbFixtures.read("genres-tv.json", TmdbDto.GenreList.class));
        when(tmdb.list(eq("/trending/all/day"), anyInt())).thenReturn(page(
                new TmdbDto.ListItem(202, "tv", false, 9.0),
                new TmdbDto.ListItem(101, "movie", false, 8.0),
                new TmdbDto.ListItem(7, "person", false, 1.0),   // pessoa: ignorada
                new TmdbDto.ListItem(303, "movie", true, 1.0),   // adulto: ignorado
                new TmdbDto.ListItem(404, "movie", false, 1.0))); // some na TMDB: falha contada
        when(tmdb.list(eq("/movie/popular"), anyInt())).thenReturn(page(new TmdbDto.ListItem(101, null, false, 8.0)));
        when(tmdb.list(eq("/tv/popular"), anyInt())).thenReturn(page(new TmdbDto.ListItem(202, null, false, 9.0)));
        when(tmdb.list(eq("/movie/top_rated"), anyInt())).thenReturn(page());
        when(tmdb.list(eq("/tv/top_rated"), anyInt())).thenReturn(page());
        when(tmdb.movie(101)).thenReturn(TmdbFixtures.movie101());
        when(tmdb.tv(202)).thenReturn(TmdbFixtures.tv202());
        when(tmdb.movie(404)).thenThrow(new TmdbException("TMDB respondeu 404", 404, null));
    }

    @Test
    void importaTitulosGenerosCreditosERankings() {
        ImportResult result = importer.run("teste");

        assertThat(result.status()).isEqualTo(ImportResult.Status.PARTIAL);
        assertThat(result.titlesSaved()).isEqualTo(2);
        assertThat(result.titlesFailed()).isEqualTo(1);
        assertThat(result.listSizes()).containsEntry("trending_day", 2).containsEntry("popular_movie", 1);

        // Drama e Comédia aparecem nas duas listas da TMDB → uma linha só, válida pros dois tipos
        assertThat(jdbc.sql("SELECT name, for_movie, for_series FROM genre WHERE external_id = 18")
                .query().singleRow())
                .containsEntry("name", "Drama").containsEntry("for_movie", true).containsEntry("for_series", true);
        assertThat(jdbc.sql("SELECT count(*) FROM genre").query(Integer.class).single()).isEqualTo(4);

        // Gênero que a TMDB não traduz recebe o nome configurado
        assertThat(jdbc.sql("SELECT name FROM genre WHERE external_id = 10765").query(String.class).single())
                .isEqualTo("Ficção científica e fantasia");

        // N:N título × gênero
        assertThat(genreSlugsOf(202, "SERIES")).containsExactlyInAnyOrder("drama", "comedia", "ficcao-cientifica-e-fantasia");
        assertThat(genreSlugsOf(101, "MOVIE")).containsExactlyInAnyOrder("ficcao-cientifica", "drama");

        // Pessoa que está nos dois títulos é gravada uma vez só
        assertThat(jdbc.sql("SELECT count(*) FROM person WHERE external_id = 10").query(Integer.class).single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM title_credit").query(Integer.class).single())
                .isEqualTo(8); // filme: 3 elenco + 2 equipe; série: 1 elenco + criadora + diretor

        // Detalhes em JSONB
        assertThat(jdbc.sql("SELECT details -> 'watchProviders' -> 'streaming' -> 0 ->> 'name' FROM title WHERE external_id = 101")
                .query(String.class).single()).isEqualTo("Max");

        // Ranking na ordem da TMDB
        assertThat(jdbc.sql("""
                SELECT t.external_id FROM title_ranking r JOIN title t ON t.id = r.title_id
                WHERE r.list_key = 'trending_day' ORDER BY r.position""").query(Integer.class).list())
                .containsExactly(202, 101);

        // Keywords (a "space" é dos dois títulos e vira uma linha só) e recomendações da TMDB
        assertThat(jdbc.sql("SELECT count(*) FROM keyword").query(Integer.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("SELECT count(*) FROM title_keyword").query(Integer.class).single()).isEqualTo(5);
        assertThat(jdbc.sql("""
                SELECT r.external_id FROM title_tmdb_recommendation r JOIN title t ON t.id = r.title_id
                WHERE t.external_id = 101 ORDER BY r.position""").query(Integer.class).list())
                .containsExactly(404, 505);

        assertThat(events.stream(CatalogImportedEvent.class)).hasSize(1);
    }

    @Test
    void rodarDuasVezesNaoDuplicaNada() {
        importer.run("primeira");
        Map<String, Object> antes = counts();

        importer.run("segunda");

        assertThat(counts()).isEqualTo(antes);
    }

    private List<String> genreSlugsOf(int externalId, String type) {
        return jdbc.sql("""
                SELECT g.slug FROM title t
                JOIN title_genre tg ON tg.title_id = t.id JOIN genre g ON g.id = tg.genre_id
                WHERE t.external_id = :id AND t.media_type = :type""")
                .param("id", externalId).param("type", type).query(String.class).list();
    }

    private Map<String, Object> counts() {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM title) titles, (SELECT count(*) FROM genre) genres,
                       (SELECT count(*) FROM title_genre) title_genres, (SELECT count(*) FROM person) people,
                       (SELECT count(*) FROM title_credit) credits, (SELECT count(*) FROM title_ranking) rankings,
                       (SELECT count(*) FROM keyword) keywords, (SELECT count(*) FROM title_keyword) title_keywords,
                       (SELECT count(*) FROM title_tmdb_recommendation) recommendations
                """).query().singleRow();
    }

    private static TmdbDto.Page<TmdbDto.ListItem> page(TmdbDto.ListItem... items) {
        return new TmdbDto.Page<>(1, List.of(items), 1, items.length);
    }
}
