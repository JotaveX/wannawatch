package br.com.jotave.wannawatch.tmdb.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.recommendation.application.TitleFeatureIndexHolder;
import br.com.jotave.wannawatch.tmdb.TmdbFixtures;
import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import br.com.jotave.wannawatch.tmdb.client.TmdbException;
import br.com.jotave.wannawatch.tmdb.client.TmdbExportClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Catálogo só para a busca: export diário da TMDB e busca na TMDB quando a daqui acha pouco.
 * Os títulos que chegam assim ficam fora dos feeds até aparecerem numa lista do dia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "wannawatch.import.search.enabled=true",
        "wannawatch.import.search.max-per-minute=1000",
        "wannawatch.import.export.max-movies=10",
        "wannawatch.import.export.max-series=10",
        "wannawatch.import.export.batch-size=1"})
class SearchCatalogImportIntegrationTest {

    @MockitoBean TmdbClient tmdb;
    @MockitoBean TmdbExportClient exports;

    @Autowired ExportImporter exportImporter;
    @Autowired CatalogImporter listImporter;
    @Autowired TitleFeatureIndexHolder indexHolder;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE title, genre, person, keyword RESTART IDENTITY CASCADE").update();
        when(tmdb.movieGenres()).thenReturn(TmdbFixtures.read("genres-movie.json", TmdbDto.GenreList.class));
        when(tmdb.tvGenres()).thenReturn(TmdbFixtures.read("genres-tv.json", TmdbDto.GenreList.class));
        when(tmdb.movie(101)).thenReturn(TmdbFixtures.movie101());
        when(tmdb.tv(202)).thenReturn(TmdbFixtures.tv202());
        when(tmdb.movie(404)).thenThrow(new TmdbException("TMDB respondeu 404", 404, null));
        when(tmdb.list(anyString(), anyInt())).thenReturn(listPage());
        when(exports.mostPopular(anyString(), eq(MediaType.MOVIE), anyInt())).thenReturn(List.of(
                new TmdbExportClient.Entry(101, false, false, 50.0),
                new TmdbExportClient.Entry(404, false, false, 10.0)));
        when(exports.mostPopular(anyString(), eq(MediaType.SERIES), anyInt())).thenReturn(List.of(
                new TmdbExportClient.Entry(202, null, null, 30.0)));
    }

    @Test
    void exportTrazOsTitulosSoParaABusca() throws Exception {
        ImportResult result = exportImporter.run("teste");

        assertThat(result.status()).isEqualTo(ImportResult.Status.PARTIAL);
        assertThat(result.titlesSaved()).isEqualTo(2);
        assertThat(result.titlesFailed()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT bool_or(in_feeds) FROM title").query(Boolean.class).single()).isFalse();

        // aparecem na busca e nos detalhes...
        mvc.perform(get("/api/search").param("q", "noite em marte"))
                .andExpect(jsonPath("$.items[0].title").value("Noite em Marte"));
        // ...mas não nos feeds nem na recomendação
        mvc.perform(get("/api/swipe/deck").param("seed", "x")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/reels")).andExpect(jsonPath("$.items", hasSize(0)));
        assertThat(indexHolder.rebuild().size()).isZero();
    }

    @Test
    void exportDeNovoSoBuscaOQueAindaNaoEstaNoBanco() {
        exportImporter.run("primeira");
        exportImporter.run("segunda");

        verify(tmdb, times(1)).movie(101);
        verify(tmdb, times(1)).tv(202);
        verify(tmdb, times(2)).movie(404); // falhou, então tenta de novo
    }

    @Test
    void tituloDoExportQueApareceNumaListaDoDiaEntraNosFeeds() throws Exception {
        exportImporter.run("export");
        when(tmdb.list(eq("/movie/popular"), anyInt())).thenReturn(listPage(new TmdbDto.ListItem(101, null, false, 8.0)));

        listImporter.run("listas");

        assertThat(jdbc.sql("SELECT in_feeds FROM title WHERE external_id = 101 AND media_type = 'MOVIE'")
                .query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT in_feeds FROM title WHERE external_id = 202 AND media_type = 'SERIES'")
                .query(Boolean.class).single()).isFalse();
        mvc.perform(get("/api/swipe/deck").param("seed", "x")).andExpect(jsonPath("$.items", hasSize(1)));

    }

    @Test
    void buscaQueNaoAchaNadaPerguntaATmdbEImportaOsResultados() throws Exception {
        when(tmdb.searchMulti("vizinhos")).thenReturn(searchPage(
                new TmdbDto.SearchItem(10, "person", false, 5.0, null),
                new TmdbDto.SearchItem(999, "movie", false, 5.0, null), // sem pôster: ignorado
                new TmdbDto.SearchItem(202, "tv", false, 9.0, "/p.jpg")));

        mvc.perform(get("/api/search").param("q", "vizinhos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Os Vizinhos de Cima"));

        verify(tmdb, never()).movie(999);
        assertThat(jdbc.sql("SELECT in_feeds FROM title WHERE external_id = 202").query(Boolean.class).single())
                .isFalse();

        // a mesma busca de novo não vai à TMDB
        mvc.perform(get("/api/search").param("q", "Vizinhos ")).andExpect(jsonPath("$.items", hasSize(1)));
        verify(tmdb, times(1)).searchMulti(any());
    }

    @Test
    void buscaQueJaAchaPeloNomeNaoVaiATmdb() throws Exception {
        exportImporter.run("export");
        jdbc.sql("UPDATE title SET title = 'Noite em Marte 2' WHERE external_id = 202").update();
        jdbc.sql("""
                INSERT INTO title (external_id, media_type, title, overview, popularity)
                VALUES (1, 'MOVIE', 'Noite em Marte 3', 'x', 1)""").update();

        mvc.perform(get("/api/search").param("q", "noite em marte")).andExpect(jsonPath("$.items", hasSize(3)));

        verify(tmdb, never()).searchMulti(any());
    }

    @Test
    void erroDaTmdbNaBuscaDevolveOQueTinha() throws Exception {
        when(tmdb.searchMulti(any())).thenThrow(new TmdbException("TMDB respondeu 500", 500, null));

        mvc.perform(get("/api/search").param("q", "qualquer coisa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void exportPeloEndpointDeAdmin() throws Exception {
        mvc.perform(post("/api/admin/import/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/import/export").header("X-Admin-Token", "test-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").isBoolean());
    }

    private static TmdbDto.Page<TmdbDto.ListItem> listPage(TmdbDto.ListItem... items) {
        return new TmdbDto.Page<>(1, List.of(items), 1, items.length);
    }

    private static TmdbDto.Page<TmdbDto.SearchItem> searchPage(TmdbDto.SearchItem... items) {
        return new TmdbDto.Page<>(1, List.of(items), 1, items.length);
    }
}
