package br.com.jotave.wannawatch.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import br.com.jotave.wannawatch.recommendation.application.TitleFeatureIndexHolder;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato e consultas da API contra um Postgres real, com um catálogo pequeno e controlado:
 *
 * <pre>
 * id  tipo    título                       pop  sinopse  gêneros            extra
 * t1  MOVIE   Vingadores: Doutor Destino   100  sim      ficção, drama      backdrop, trailer
 * t2  MOVIE   A Casa no Fim da Rua          90  sim      terror             backdrop
 * t3  SERIES  Os Vizinhos de Cima           80  sim      comédia, drama     2 temporadas, Pedro Pascal
 * t4  SERIES  Fórmula do Caos               70  NÃO      drama
 * t5  MOVIE   Noite em Marte                60  sim      ficção             dir. Christopher Nolan
 * t6  MOVIE   Sinal Fechado                 60  sim      drama
 * t7  SERIES  Ilha dos Ecos                 50  sim      terror, drama      1 temporada
 * trending_day: t3, t1, t2   ·   top_movie: t5, t1
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired TitleFeatureIndexHolder indexHolder;

    long t1, t2, t3, t4, t5, t6, t7;

    @BeforeEach
    void seed() {
        jdbc.sql("TRUNCATE title, genre, person RESTART IDENTITY CASCADE").update();
        long drama = genre(18, "Drama", "drama", true, true);
        long terror = genre(27, "Terror", "terror", true, false);
        long scifi = genre(878, "Ficção científica", "ficcao-cientifica", true, false);
        long comedy = genre(35, "Comédia", "comedia", true, true);

        t1 = title(1, "MOVIE", "Vingadores: Doutor Destino", "Avengers: Doomsday", "Heróis contra o Destino.", 100, "/b1.jpg", null, scifi, drama);
        t2 = title(2, "MOVIE", "A Casa no Fim da Rua", null, "Uma casa barata demais.", 90, "/b2.jpg", null, terror);
        t3 = title(3, "SERIES", "Os Vizinhos de Cima", null, "Três amigos e um barulho.", 80, null, 2, comedy, drama);
        t4 = title(4, "SERIES", "Fórmula do Caos", null, null, 70, null, 1, drama);
        t5 = title(5, "MOVIE", "Noite em Marte", null, "Uma astronauta em Marte.", 60, null, null, scifi);
        t6 = title(6, "MOVIE", "Sinal Fechado", null, "Um encontro no trânsito.", 60, null, null, drama);
        t7 = title(7, "SERIES", "Ilha dos Ecos", null, "Ninguém sai da ilha.", 50, null, 1, terror, drama);
        jdbc.sql("UPDATE title SET trailer_youtube_key = 'abc123', tagline = 'O fim começa agora' WHERE id = :id")
                .param("id", t1).update();

        credit(t3, person(100, "Pedro Pascal"), "CAST", "Vizinho", null, 0);
        credit(t5, person(200, "Christopher Nolan"), "CREW", null, "Director", 0);

        rank("trending_day", t3, t1, t2);
        rank("top_movie", t5, t1);
        // o catálogo foi montado direto no banco: o índice de recomendação precisa saber
        indexHolder.rebuild();
    }

    @Nested
    class Generos {

        @Test
        void filtraPorTipoEContaTitulos() throws Exception {
            mvc.perform(get("/api/genres").param("type", "SERIES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].slug", contains("comedia", "drama")))
                    .andExpect(jsonPath("$[?(@.slug == 'drama')].titleCount", contains(3)));
        }
    }

    @Nested
    class Titulos {

        @Test
        void paginaPorGeneroOrdenandoPorPopularidade() throws Exception {
            mvc.perform(get("/api/titles").param("genre", "drama").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items[*].id", contains((int) t1, (int) t3)));

            mvc.perform(get("/api/titles").param("genre", "drama").param("size", "2").param("page", "2"))
                    .andExpect(jsonPath("$.items[*].id", contains((int) t7)));
        }

        @Test
        void combinaTipoEGenero() throws Exception {
            mvc.perform(get("/api/titles").param("type", "SERIES").param("genre", "drama"))
                    .andExpect(jsonPath("$.items[*].id", contains((int) t3, (int) t4, (int) t7)));
        }

        @Test
        void cardTemGenerosNaOrdemDaTmdbEImagensComUrlCompleta() throws Exception {
            mvc.perform(get("/api/titles").param("size", "1"))
                    .andExpect(jsonPath("$.items[0].title").value("Vingadores: Doutor Destino"))
                    .andExpect(jsonPath("$.items[0].mediaType").value("MOVIE"))
                    .andExpect(jsonPath("$.items[0].year").value(2025))
                    .andExpect(jsonPath("$.items[0].genres[*].slug", contains("ficcao-cientifica", "drama")))
                    .andExpect(jsonPath("$.items[0].poster.small").value("https://image.tmdb.org/t/p/w342/p1.jpg"))
                    .andExpect(jsonPath("$.items[0].backdrop.large").value("https://image.tmdb.org/t/p/w1280/b1.jpg"));
        }

        @Test
        void tamanhoDePaginaForaDoLimiteDa400() throws Exception {
            mvc.perform(get("/api/titles").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")));
        }

        @Test
        void tipoInvalidoDa400() throws Exception {
            mvc.perform(get("/api/titles").param("type", "PODCAST")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Detalhe {

        @Test
        void trazTrailerCreditosEParecidosPreferindoOMesmoTipo() throws Exception {
            mvc.perform(get("/api/titles/{id}", t1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Vingadores: Doutor Destino"))
                    .andExpect(jsonPath("$.tagline").value("O fim começa agora"))
                    .andExpect(jsonPath("$.trailerUrl").value("https://www.youtube.com/watch?v=abc123"))
                    .andExpect(jsonPath("$.watchProviders.streaming", hasSize(0)))
                    .andExpect(jsonPath("$.similar[0].mediaType").value("MOVIE"))
                    .andExpect(jsonPath("$.similar[1].mediaType").value("MOVIE"));

            mvc.perform(get("/api/titles/{id}", t5))
                    .andExpect(jsonPath("$.crew[0].name").value("Christopher Nolan"))
                    .andExpect(jsonPath("$.crew[0].job").value("Direção"));
        }

        @Test
        void tituloInexistenteDa404EmProblemDetail() throws Exception {
            mvc.perform(get("/api/titles/{id}", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Título 999999 não encontrado."));
        }
    }

    @Nested
    class Reels {

        @Test
        void percorreTudoPeloCursorSemRepetirNemPularESemTitulosSemSinopse() throws Exception {
            List<Long> all = walk("/api/reels", Map.of("size", "2"));
            // t5 e t6 empatam em popularidade: o id desempata (decrescente)
            assertThat(all).containsExactly(t1, t2, t3, t6, t5, t7);
        }

        @Test
        void filtraPorGenero() throws Exception {
            assertThat(walk("/api/reels", Map.of("size", "1", "genre", "terror"))).containsExactly(t2, t7);
        }

        @Test
        void cursorInvalidoDa400() throws Exception {
            mvc.perform(get("/api/reels").param("cursor", "nao-e-um-cursor"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Cursor inválido."));
        }
    }

    @Nested
    class Swipe {

        @Test
        void mesmaSeedDaMesmaOrdemESemRepeticao() throws Exception {
            List<Long> a = walk("/api/swipe/deck", Map.of("seed", "sessao-a", "size", "4"));
            List<Long> again = walk("/api/swipe/deck", Map.of("seed", "sessao-a", "size", "2"));
            List<Long> b = walk("/api/swipe/deck", Map.of("seed", "sessao-b", "size", "4"));

            assertThat(a).hasSize(6).doesNotHaveDuplicates().doesNotContain(t4);
            assertThat(again).isEqualTo(a);
            assertThat(b).containsExactlyInAnyOrderElementsOf(a).isNotEqualTo(a);
        }

        @Test
        void seedEhObrigatoria() throws Exception {
            mvc.perform(get("/api/swipe/deck")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/swipe/deck").param("seed", "com espaço")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Busca {

        @Test
        void achaComErroDeDigitacaoEPeloComecoDaPalavra() throws Exception {
            mvc.perform(get("/api/search").param("q", "vingadres"))
                    .andExpect(jsonPath("$.items[0].id").value(t1))
                    .andExpect(jsonPath("$.items[0].matchedBy.kind").value("TITLE"))
                    .andExpect(jsonPath("$.items[0].matchedBy.label").value(nullValue()));
            mvc.perform(get("/api/search").param("q", "vinga"))
                    .andExpect(jsonPath("$.items[0].id").value(t1));
        }

        @Test
        void achaPeloTituloOriginalESemAcento() throws Exception {
            mvc.perform(get("/api/search").param("q", "doomsday")).andExpect(jsonPath("$.items[0].id").value(t1));
            mvc.perform(get("/api/search").param("q", "formula do caos")).andExpect(jsonPath("$.items[0].id").value(t4));
        }

        @Test
        void achaPorElencoEPorEquipe() throws Exception {
            mvc.perform(get("/api/search").param("q", "pascal"))
                    .andExpect(jsonPath("$.items[0].id").value(t3))
                    .andExpect(jsonPath("$.items[0].matchedBy.kind").value("PERSON"))
                    .andExpect(jsonPath("$.items[0].matchedBy.label").value("com Pedro Pascal"));
            mvc.perform(get("/api/search").param("q", "nolan"))
                    .andExpect(jsonPath("$.items[0].matchedBy.label").value("direção de Christopher Nolan"));
        }

        @Test
        void achaPorGeneroOrdenandoPorPopularidade() throws Exception {
            mvc.perform(get("/api/search").param("q", "terror"))
                    .andExpect(jsonPath("$.totalItems").value(2))
                    .andExpect(jsonPath("$.items[*].id", contains((int) t2, (int) t7)))
                    .andExpect(jsonPath("$.items[0].matchedBy.kind").value("GENRE"))
                    .andExpect(jsonPath("$.items[0].matchedBy.label").value("Terror"));
        }

        @Test
        void buscaCurtaDemaisDa400() throws Exception {
            mvc.perform(get("/api/search").param("q", "a")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Home {

        @Test
        void montaDestaqueEFileiras() throws Exception {
            mvc.perform(get("/api/home"))
                    .andExpect(status().isOk())
                    // t3 é o 1º do trending, mas não tem backdrop: o destaque é o t1
                    .andExpect(jsonPath("$.hero.id").value(t1))
                    .andExpect(jsonPath("$.hero.tagline").value("O fim começa agora"))
                    .andExpect(jsonPath("$.rows[0].kind").value("TOP10"))
                    .andExpect(jsonPath("$.rows[0].items[*].id", contains((int) t3, (int) t1, (int) t2)))
                    .andExpect(jsonPath("$.rows[1].kind").value("SIMILAR"))
                    .andExpect(jsonPath("$.rows[1].title").value("Porque você curtiu Vingadores: Doutor Destino"))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'BINGE')].items[*].id", contains((int) t3)))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'TOP_RATED')].items[*].id", contains((int) t5, (int) t1)));
        }
    }

    @Test
    void corsLiberaOFrontEmDev() throws Exception {
        mvc.perform(options("/api/home")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    // ------------------------------------------------------------------ helpers

    /** Segue o nextCursor até o fim e devolve todos os ids na ordem recebida. */
    private List<Long> walk(String path, Map<String, String> params) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        var seen = new HashSet<String>();
        do {
            var req = get(path);
            params.forEach(req::param);
            if (cursor != null) req.param("cursor", cursor);
            String json = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(json, "$.items[*].id");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(json, "$.nextCursor");
            assertThat(cursor == null || seen.add(cursor)).as("cursor não pode repetir").isTrue();
        } while (cursor != null);
        return ids;
    }

    private long genre(int ext, String name, String slug, boolean movie, boolean series) {
        return jdbc.sql("""
                INSERT INTO genre (external_id, name, slug, for_movie, for_series)
                VALUES (:e, :n, :s, :m, :t) RETURNING id""")
                .param("e", ext).param("n", name).param("s", slug).param("m", movie).param("t", series)
                .query(Long.class).single();
    }

    private long title(int ext, String type, String name, String original, String overview, double popularity,
                       String backdrop, Integer seasons, long... genreIds) {
        long id = jdbc.sql("""
                INSERT INTO title (external_id, media_type, title, original_title, overview, popularity,
                                   release_date, poster_path, backdrop_path, seasons_count)
                VALUES (:e, :type, :title, :orig, :ov, :pop, DATE '2025-06-01', :poster, :backdrop, :seasons)
                RETURNING id""")
                .param("e", ext).param("type", type).param("title", name).param("orig", original)
                .param("ov", overview).param("pop", popularity).param("poster", "/p" + ext + ".jpg")
                .param("backdrop", backdrop).param("seasons", seasons)
                .query(Long.class).single();
        for (int i = 0; i < genreIds.length; i++) {
            jdbc.sql("INSERT INTO title_genre (title_id, genre_id, position) VALUES (:t, :g, :p)")
                    .param("t", id).param("g", genreIds[i]).param("p", i).update();
        }
        return id;
    }

    private long person(int ext, String name) {
        return jdbc.sql("INSERT INTO person (external_id, name) VALUES (:e, :n) RETURNING id")
                .param("e", ext).param("n", name).query(Long.class).single();
    }

    private void credit(long titleId, long personId, String type, String character, String job, int order) {
        jdbc.sql("""
                INSERT INTO title_credit (title_id, person_id, credit_type, character, job, credit_order)
                VALUES (:t, :p, :type, :c, :j, :o)""")
                .param("t", titleId).param("p", personId).param("type", type)
                .param("c", character).param("j", job).param("o", order).update();
    }

    private void rank(String list, long... ids) {
        for (int i = 0; i < ids.length; i++) {
            jdbc.sql("INSERT INTO title_ranking (list_key, position, title_id) VALUES (:l, :p, :t)")
                    .param("l", list).param("p", i + 1).param("t", ids[i]).update();
        }
    }
}
