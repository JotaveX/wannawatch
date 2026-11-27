package br.com.jotave.wannawatch.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import br.com.jotave.wannawatch.recommendation.application.RecommendationService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Home, Swipe e Reels personalizados contra um Postgres real. Catálogo de 14 títulos com gêneros e
 * pessoas distintos: 8 de ficção científica (dois com a mesma direção), 2 comédias, 2 de terror,
 * 1 drama e 1 animação. O mesmo ator está numa ficção científica e numa comédia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecommendationApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired TitleFeatureIndexHolder indexHolder;
    @Autowired RecommendationService recommendations;

    long s1, s2, s3, s4, s5, s6, s7, s8, c1, c2, h1, h2, d1, a1;
    String jotave, bia;

    @BeforeEach
    void seed() throws Exception {
        jdbc.sql("TRUNCATE title, genre, person, keyword RESTART IDENTITY CASCADE").update();
        jdbc.sql("DELETE FROM auth_session").update();
        jdbc.sql("DELETE FROM interaction_event").update();
        jdbc.sql("UPDATE app_user SET favorite_genre_id = NULL, favorite_title_id = NULL").update();

        long scifi = genre(878, "Ficção científica", "ficcao-cientifica");
        long comedy = genre(35, "Comédia", "comedia");
        long horror = genre(27, "Terror", "terror");
        long drama = genre(18, "Drama", "drama");
        long animation = genre(16, "Animação", "animacao");

        s1 = title(1, "Duna", 50, "/b1.jpg", scifi);
        s2 = title(2, "A Chegada", 40, null, scifi, drama);
        s3 = title(3, "Interestelar", 60, null, scifi);
        s4 = title(4, "Noite em Marte", 20, null, scifi);
        s5 = title(5, "Ex Machina", 15, null, scifi);
        s6 = title(6, "Solaris", 12, null, scifi);
        s7 = title(7, "Gravidade", 11, null, scifi);
        s8 = title(8, "Contato", 10, null, scifi);
        c1 = title(9, "Comédia Um", 100, "/b9.jpg", comedy);
        c2 = title(10, "Comédia Dois", 90, null, comedy);
        h1 = title(11, "Terror Um", 80, "/b11.jpg", horror);
        h2 = title(12, "Terror Dois", 70, null, horror);
        d1 = title(13, "Drama Um", 85, null, drama);
        a1 = title(14, "Animação Um", 30, null, animation);

        long villeneuve = person(100, "Denis Villeneuve");
        credit(s1, villeneuve, "CREW", "Director", 0);
        credit(s2, villeneuve, "CREW", "Director", 0);
        long mcconaughey = person(200, "Matthew McConaughey");
        credit(s3, mcconaughey, "CAST", null, 0);
        credit(c1, mcconaughey, "CAST", null, 0);

        rank("trending_day", h1, s1, c1);
        indexHolder.rebuild();
        recommendations.clearCache();
        jotave = login("jotave");
        bia = login("bia");
    }

    @Nested
    class Home {

        @Test
        void quemCurteFiccaoCientificaRecebeFiccaoCientificaNoTopoDoPraVoce() throws Exception {
            for (long id : List.of(s1, s2, s3, s4, s5)) like(jotave, id);

            mvc.perform(get("/api/home").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[1].kind").value("FOR_YOU"))
                    .andExpect(jsonPath("$.rows[1].title").value("Pra você"))
                    .andExpect(jsonPath("$.rows[1].items[0].genres[0].slug").value("ficcao-cientifica"))
                    // o que eu já curti não aparece de novo
                    .andExpect(jsonPath("$.rows[1].items[*].id", not(hasItem((int) s1))))
                    .andExpect(jsonPath("$.rows[1].items[*].reason.text", everyItem(notNullValue())))
                    .andExpect(jsonPath("$.rows[1].items[0].reason.kind").value("BECAUSE_LIKED"));
        }

        @Test
        void diversidadeLimitaOMesmoGeneroNoTopoDoPraVoce() throws Exception {
            for (long id : List.of(s1, s2, s3, s4)) like(jotave, id);
            String json = home(jotave);
            List<String> top = JsonPath.read(json, "$.rows[?(@.kind == 'FOR_YOU')].items[*].genres[0].slug");
            // sobram 6 títulos de outros gêneros: os 9 primeiros cabem no limite de 3 por gênero;
            // a partir daí não há o que intercalar e a ordem do score volta a valer
            assertThat(top.subList(0, 9).stream().filter("ficcao-cientifica"::equals).count()).isLessThanOrEqualTo(3);
        }

        @Test
        void porqueVoceCurtiuUsaUmaCurtidaDeVerdade() throws Exception {
            like(jotave, s1);
            signal(jotave, s2, "SEEN");

            mvc.perform(get("/api/home").header("Authorization", "Bearer " + jotave))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'SIMILAR')].title", org.hamcrest.Matchers.contains("Porque você curtiu Duna")))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'SIMILAR')].items[*].id", not(hasItem((int) s1))))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'SIMILAR')].items[*].id", not(hasItem((int) s2))));
        }

        @Test
        void semCurtidaNaoTemFileiraDeParecidos() throws Exception {
            mvc.perform(get("/api/home").header("Authorization", "Bearer " + jotave))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'SIMILAR')]").isEmpty())
                    .andExpect(jsonPath("$.rows[?(@.kind == 'FOR_YOU')]").isNotEmpty());
        }

        @Test
        void destaqueEFileiraDeGeneroSeguemOGosto() throws Exception {
            for (long id : List.of(s2, s3, s4, s5, s6)) like(jotave, id);

            mvc.perform(get("/api/home").header("Authorization", "Bearer " + jotave))
                    // h1 é o 1º do trending, mas entre os que têm backdrop o Duna combina mais
                    .andExpect(jsonPath("$.hero.id").value(s1))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'GENRE')].title", hasItem("Ficção científica")));
        }

        @Test
        void semTokenNaoPersonalizaNada() throws Exception {
            like(jotave, s1);
            mvc.perform(get("/api/home"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hero.id").value(h1))
                    .andExpect(jsonPath("$.rows[?(@.kind == 'FOR_YOU')]").isEmpty())
                    .andExpect(jsonPath("$.rows[0].items[0].reason").value(nullValue()));
        }

        @Test
        void tokenInvalidoContinuaDando401() throws Exception {
            mvc.perform(get("/api/home").header("Authorization", "Bearer nao-existe")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/reels").header("Authorization", "Bearer nao-existe")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Swipe {

        @Test
        void naoMostraOQueJaFoiAvaliado() throws Exception {
            like(jotave, s1);
            signal(jotave, c1, "PASS");
            signal(jotave, h1, "SEEN");
            signal(jotave, h2, "SAVED");
            signal(jotave, d1, "MAYBE");

            List<Long> deck = walk(jotave, "/api/swipe/deck", Map.of("seed", "a", "size", "4"), null);

            assertThat(deck).doesNotContain(s1, c1, h1, h2, d1).hasSize(9);
        }

        @Test
        void talvezAntigoVoltaAoDeck() throws Exception {
            signal(jotave, d1, "MAYBE");
            jdbc.sql("UPDATE user_title_signal SET created_at = now() - interval '8 days'").update();
            recommendations.clearCache();

            assertThat(walk(jotave, "/api/swipe/deck", Map.of("seed", "a", "size", "20"), null)).contains(d1);
        }

        @Test
        void paginacaoNaoRepeteNemPulaMesmoComCurtidaNoMeio() throws Exception {
            List<Long> deck = walk(jotave, "/api/swipe/deck", Map.of("seed", "b", "size", "3"),
                    // depois de cada página, curte o primeiro: muda o score de tudo
                    ids -> like(jotave, ids.getFirst()));

            assertThat(deck).hasSize(14).doesNotHaveDuplicates();
        }

        @Test
        void soOsQueMaisCombinamVemComMotivoPessoal() throws Exception {
            // 5 curtidas: sai do cold start (antes disso o topo é só popularidade, sem motivo pessoal)
            for (long id : List.of(s1, s2, s3, s4, s5)) like(jotave, id);
            String body = mvc.perform(get("/api/swipe/deck").param("seed", "c").param("size", "20")
                            .header("Authorization", "Bearer " + jotave))
                    .andReturn().getResponse().getContentAsString();
            List<String> kinds = JsonPath.read(body, "$.items[*].reason.kind");
            List<Object> items = JsonPath.read(body, "$.items");

            // percentil ≥ 0,9 do deck (9 títulos): só o do topo tem motivo, e nunca "Em alta"
            assertThat(items).hasSize(9);
            assertThat(kinds).hasSize(1).doesNotContain("POPULAR");
        }

        @Test
        void semTokenSegueAleatorioPorSeedESemMotivo() throws Exception {
            mvc.perform(get("/api/swipe/deck").param("seed", "c"))
                    .andExpect(jsonPath("$.items[0].reason").value(nullValue()))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        }

        @Test
        void cursorDeOutroFormatoDa400() throws Exception {
            String anon = JsonPath.read(mvc.perform(get("/api/swipe/deck").param("seed", "c").param("size", "2"))
                    .andReturn().getResponse().getContentAsString(), "$.nextCursor");
            mvc.perform(get("/api/swipe/deck").param("seed", "c").param("cursor", anon)
                            .header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Reels {

        @Test
        void tiraJaVistoEPassadoEDeixaOsCurtidosNoFim() throws Exception {
            signal(jotave, c1, "SEEN");
            signal(jotave, c2, "PASS");
            like(jotave, s8);

            List<Long> reels = walk(jotave, "/api/reels", Map.of("size", "5"), null);

            assertThat(reels).doesNotContain(c1, c2).hasSize(12).endsWith(s8);
        }
    }

    @Nested
    class Perfil {

        @Test
        void trocarOsFavoritosAtualizaAsRecomendacoesNaHora() throws Exception {
            long jotaveId = jdbc.sql("SELECT id FROM app_user WHERE username = 'jotave'").query(Long.class).single();
            assertThat(recommendations.profile(jotaveId).favoriteGenreId()).isNull(); // fica em cache
            long horror = jdbc.sql("SELECT id FROM genre WHERE slug = 'terror'").query(Long.class).single();

            mvc.perform(put("/api/me/profile").header("Authorization", "Bearer " + jotave)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Jotave\",\"color\":0,\"favoriteGenreId\":" + horror
                                    + ",\"favoriteTitleId\":" + s1 + "}"))
                    .andExpect(status().isOk());

            var profile = recommendations.profile(jotaveId);
            assertThat(profile.favoriteGenreId()).isEqualTo(horror);
            assertThat(profile.positiveSignals()).isEqualTo(1);
        }

        @Test
        void votoDoMatchContaMesmoDepoisDeSairDaSala() throws Exception {
            String code = JsonPath.read(mvc.perform(post("/api/match/rooms").header("Authorization", "Bearer " + bia)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getContentAsString(), "$.code");
            mvc.perform(post("/api/match/rooms/" + code + "/join").header("Authorization", "Bearer " + jotave));
            mvc.perform(post("/api/match/rooms/" + code + "/start").header("Authorization", "Bearer " + bia));
            vote(bia, code, s1, true);
            vote(bia, code, c1, false);
            vote(bia, code, h1, true);
            mvc.perform(delete("/api/match/rooms/" + code + "/votes/" + h1).header("Authorization", "Bearer " + bia));
            mvc.perform(delete("/api/match/rooms/" + code + "/participants/me").header("Authorization", "Bearer " + bia))
                    .andExpect(status().isNoContent());

            long biaId = jdbc.sql("SELECT id FROM app_user WHERE username = 'bia'").query(Long.class).single();
            var profile = recommendations.profile(biaId);
            assertThat(profile.positiveSignals()).isEqualTo(1); // s1; o voto em h1 foi desfeito
            assertThat(profile.negative().isEmpty()).isFalse(); // c1
        }

        @Test
        void votoDeAntesDoLogTambemConta() throws Exception {
            String code = JsonPath.read(mvc.perform(post("/api/match/rooms").header("Authorization", "Bearer " + bia)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getContentAsString(), "$.code");
            mvc.perform(post("/api/match/rooms/" + code + "/join").header("Authorization", "Bearer " + jotave));
            mvc.perform(post("/api/match/rooms/" + code + "/start").header("Authorization", "Bearer " + bia));
            vote(bia, code, s1, true);
            jdbc.sql("DELETE FROM interaction_event").update(); // como se o voto fosse de antes da V7
            recommendations.clearCache();

            long biaId = jdbc.sql("SELECT id FROM app_user WHERE username = 'bia'").query(Long.class).single();
            assertThat(recommendations.profile(biaId).positiveSignals()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ helpers

    interface PageHook {
        void afterPage(List<Long> ids) throws Exception;
    }

    private List<Long> walk(String token, String path, Map<String, String> params, PageHook hook) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        var cursors = new HashSet<String>();
        do {
            var req = get(path).header("Authorization", "Bearer " + token);
            params.forEach(req::param);
            if (cursor != null) req.param("cursor", cursor);
            String json = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(json, "$.items[*].id");
            List<Long> pageIds = page.stream().map(Number::longValue).toList();
            ids.addAll(pageIds);
            cursor = JsonPath.read(json, "$.nextCursor");
            assertThat(cursor == null || cursors.add(cursor)).as("cursor não pode repetir").isTrue();
            if (hook != null && !pageIds.isEmpty()) hook.afterPage(pageIds);
        } while (cursor != null);
        return ids;
    }

    private String home(String token) throws Exception {
        return mvc.perform(get("/api/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wannawatch\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private void like(String token, long titleId) throws Exception {
        mvc.perform(put("/api/likes/" + titleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void signal(String token, long titleId, String kind) throws Exception {
        mvc.perform(put("/api/titles/" + titleId + "/signals/" + kind).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void vote(String token, String code, long titleId, boolean liked) throws Exception {
        mvc.perform(put("/api/match/rooms/" + code + "/votes/" + titleId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":" + liked + "}"))
                .andExpect(status().isOk());
    }

    private long genre(int ext, String name, String slug) {
        return jdbc.sql("""
                INSERT INTO genre (external_id, name, slug, for_movie, for_series) VALUES (:e, :n, :s, true, true)
                RETURNING id""")
                .param("e", ext).param("n", name).param("s", slug).query(Long.class).single();
    }

    private long title(int ext, String name, double popularity, String backdrop, long... genreIds) {
        long id = jdbc.sql("""
                INSERT INTO title (external_id, media_type, title, overview, popularity, release_date, backdrop_path,
                                   vote_average, vote_count)
                VALUES (:e, 'MOVIE', :title, 'Sinopse.', :pop, DATE '2020-06-01', :backdrop, 7.5, 1000) RETURNING id""")
                .param("e", ext).param("title", name).param("pop", popularity).param("backdrop", backdrop)
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

    private void credit(long titleId, long personId, String type, String job, int order) {
        jdbc.sql("""
                INSERT INTO title_credit (title_id, person_id, credit_type, job, credit_order)
                VALUES (:t, :p, :type, :job, :o)""")
                .param("t", titleId).param("p", personId).param("type", type).param("job", job).param("o", order)
                .update();
    }

    private void rank(String list, long... ids) {
        for (int i = 0; i < ids.length; i++) {
            jdbc.sql("INSERT INTO title_ranking (list_key, position, title_id) VALUES (:k, :p, :t)")
                    .param("k", list).param("p", i + 1).param("t", ids[i]).update();
        }
    }
}
