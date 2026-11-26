package br.com.jotave.wannawatch.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Login, curtidas e salas de Match contra um Postgres real. Usa as 3 contas criadas pela
 * migration V4 (jotave, bia, caio; senha "wannawatch") e um catálogo de 4 títulos:
 * m1..m3 filmes de drama, s1 série de comédia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SocialApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    long m1, m2, m3, s1;
    String jotave, bia, caio;

    @BeforeEach
    void seed() throws Exception {
        // CASCADE limpa curtidas e salas (que apontam para title)
        jdbc.sql("TRUNCATE title, genre, person RESTART IDENTITY CASCADE").update();
        jdbc.sql("DELETE FROM auth_session").update();
        // o log não tem FK para title, então o TRUNCATE não chega nele
        jdbc.sql("DELETE FROM interaction_event").update();
        // volta os perfis ao que a migration deixou
        jdbc.sql("""
                UPDATE app_user SET display_name = initcap(username), color = (id - 1) % 5, mascot = NULL,
                       favorite_genre_id = NULL, favorite_title_id = NULL""").update();
        long drama = genre(18, "Drama", "drama");
        long comedy = genre(35, "Comédia", "comedia");
        m1 = title(1, "MOVIE", "Filme Um", drama);
        m2 = title(2, "MOVIE", "Filme Dois", drama);
        m3 = title(3, "MOVIE", "Filme Três", drama);
        s1 = title(4, "SERIES", "Série Um", comedy);
        jotave = login("jotave");
        bia = login("bia");
        caio = login("caio");
    }

    @Nested
    class Login {

        @Test
        void devolveTokenEUsuario() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\" Bia \",\"password\":\"wannawatch\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", matchesPattern("[A-Za-z0-9_-]{43}")))
                    .andExpect(jsonPath("$.user.username").value("bia"))
                    .andExpect(jsonPath("$.user.displayName").value("Bia"));
        }

        @Test
        void senhaErradaDa401() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"bia\",\"password\":\"errada\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Usuário ou senha incorretos."));
        }

        @Test
        void meExigeTokenValido() throws Exception {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bia))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("bia"));
            mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer inventado"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void logoutInvalidaOToken() throws Exception {
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + bia))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bia))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void catalogoContinuaAberto() throws Exception {
            mvc.perform(get("/api/titles")).andExpect(status().isOk());
        }

        @Test
        void corsAceitaEscritaComAuthorization() throws Exception {
            mvc.perform(options("/api/likes/1")
                            .header("Origin", "http://localhost:4200")
                            .header("Access-Control-Request-Method", "PUT")
                            .header("Access-Control-Request-Headers", "authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        }
    }

    @Nested
    class Curtidas {

        @Test
        void mostraQuemCurtiuSemMeContarComoAmigo() throws Exception {
            like(jotave, m1);
            like(bia, m1);
            like(caio, m1);
            like(bia, m2);

            mvc.perform(get("/api/likes").param("titleIds", m1 + "," + m2 + "," + m3).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].titleId", contains((int) m1, (int) m2, (int) m3)))
                    .andExpect(jsonPath("$[0].likedByMe").value(true))
                    .andExpect(jsonPath("$[0].friends[*].username", contains("caio", "bia")))
                    .andExpect(jsonPath("$[1].likedByMe").value(false))
                    .andExpect(jsonPath("$[2].friends", hasSize(0)));
        }

        @Test
        void curtirEDescurtirSaoIdempotentes() throws Exception {
            like(bia, m1);
            like(bia, m1);
            mvc.perform(delete("/api/likes/" + m1).header("Authorization", "Bearer " + bia)).andExpect(status().isNoContent());
            mvc.perform(delete("/api/likes/" + m1).header("Authorization", "Bearer " + bia)).andExpect(status().isNoContent());
            assertThat(jdbc.sql("SELECT count(*) FROM title_like").query(Integer.class).single()).isZero();
        }

        @Test
        void tituloInexistenteDa404() throws Exception {
            mvc.perform(put("/api/likes/9999").header("Authorization", "Bearer " + bia)).andExpect(status().isNotFound());
        }

        @Test
        void fileiraDosAmigosOrdenaPorQuantidade() throws Exception {
            like(bia, m2);
            like(bia, m1);
            like(caio, m1);
            like(jotave, m3); // só eu curti: não entra
            like(jotave, m2);

            mvc.perform(get("/api/likes/friends").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].title.id", contains((int) m1, (int) m2)))
                    .andExpect(jsonPath("$[0].friends", hasSize(2)))
                    .andExpect(jsonPath("$[0].likedByMe").value(false))
                    .andExpect(jsonPath("$[1].likedByMe").value(true));
        }

        @Test
        void exigeLogin() throws Exception {
            mvc.perform(put("/api/likes/" + m1)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Sinais {

        @Test
        void criaEListaOsSinaisDeCadaTitulo() throws Exception {
            signal(bia, m1, "seen").andExpect(status().isNoContent());
            signal(bia, m1, "SAVED").andExpect(status().isNoContent());
            signal(bia, m2, "pass").andExpect(status().isNoContent());
            signal(caio, m3, "maybe").andExpect(status().isNoContent()); // de outra pessoa: não aparece

            mvc.perform(get("/api/me/signals").param("ids", m1 + "," + m2 + "," + m3).header("Authorization", "Bearer " + bia))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].titleId", contains((int) m1, (int) m2, (int) m3)))
                    .andExpect(jsonPath("$[0].kinds", containsInAnyOrder("SEEN", "SAVED")))
                    .andExpect(jsonPath("$[1].kinds", contains("PASS")))
                    .andExpect(jsonPath("$[2].kinds", hasSize(0)));
        }

        @Test
        void criarERemoverSaoIdempotentesSemDuplicarOLog() throws Exception {
            signal(bia, m1, "SEEN").andExpect(status().isNoContent());
            signal(bia, m1, "SEEN").andExpect(status().isNoContent());
            assertThat(count("SELECT count(*) FROM user_title_signal")).isEqualTo(1);

            unsignal(bia, m1, "SEEN").andExpect(status().isNoContent());
            unsignal(bia, m1, "SEEN").andExpect(status().isNoContent());
            assertThat(count("SELECT count(*) FROM user_title_signal")).isZero();

            assertThat(events()).containsExactly("SEEN:null", "SIGNAL_REMOVED:SEEN");
        }

        @Test
        void eventoGuardaOrigemEIdDaTmdb() throws Exception {
            mvc.perform(put("/api/titles/" + m2 + "/signals/PASS").param("source", "swipe")
                            .header("Authorization", "Bearer " + bia))
                    .andExpect(status().isNoContent());
            var row = jdbc.sql("SELECT title_external_id, media_type, title_id, source FROM interaction_event")
                    .query().singleRow();
            assertThat(row).containsEntry("title_external_id", 2).containsEntry("media_type", "MOVIE")
                    .containsEntry("title_id", m2).containsEntry("source", "SWIPE");
        }

        @Test
        void talvezDeNovoRenovaAData() throws Exception {
            signal(bia, m1, "MAYBE").andExpect(status().isNoContent());
            jdbc.sql("UPDATE user_title_signal SET created_at = now() - interval '10 days'").update();
            signal(bia, m1, "MAYBE").andExpect(status().isNoContent());
            assertThat(count("SELECT count(*) FROM user_title_signal WHERE created_at > now() - interval '1 day'"))
                    .isEqualTo(1);
        }

        @Test
        void curtidaEVotoTambemVaoParaOLog() throws Exception {
            mvc.perform(put("/api/likes/" + m1).param("source", "REELS").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNoContent());
            like(jotave, m1); // repetido: não loga
            mvc.perform(delete("/api/likes/" + m1).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNoContent());

            String code = create(jotave, "{}");
            join(bia, code);
            start(jotave, code);
            vote(jotave, code, m2, true);
            vote(jotave, code, m3, false);
            mvc.perform(delete("/api/match/rooms/" + code + "/votes/" + m3).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk());

            assertThat(events()).containsExactly("LIKE:null", "UNLIKE:null", "MATCH_VOTE_YES:null",
                    "MATCH_VOTE_NO:null", "MATCH_UNVOTE:null");
            assertThat(count("SELECT count(*) FROM interaction_event WHERE kind LIKE 'MATCH%' AND source = 'MATCH' AND room_id IS NOT NULL"))
                    .isEqualTo(3);
        }

        @Test
        void sinalDesconhecidoDa400() throws Exception {
            signal(bia, m1, "LIKE").andExpect(status().isBadRequest());
            mvc.perform(put("/api/titles/" + m1 + "/signals/SEEN").param("source", "CINEMA")
                            .header("Authorization", "Bearer " + bia))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void tituloInexistenteDa404() throws Exception {
            signal(bia, 9999, "SEEN").andExpect(status().isNotFound());
        }

        @Test
        void exigeLogin() throws Exception {
            mvc.perform(put("/api/titles/" + m1 + "/signals/SEEN")).andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/titles/" + m1 + "/signals/SEEN")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/me/signals").param("ids", "1")).andExpect(status().isUnauthorized());
        }

        private ResultActions signal(String token, long titleId, String kind) throws Exception {
            return mvc.perform(put("/api/titles/" + titleId + "/signals/" + kind).header("Authorization", "Bearer " + token));
        }

        private ResultActions unsignal(String token, long titleId, String kind) throws Exception {
            return mvc.perform(delete("/api/titles/" + titleId + "/signals/" + kind).header("Authorization", "Bearer " + token));
        }

        private int count(String sql) {
            return jdbc.sql(sql).query(Integer.class).single();
        }

        /** "KIND:value" de cada evento, na ordem em que foram gravados. */
        private List<String> events() {
            return jdbc.sql("SELECT kind || ':' || coalesce(value, 'null') FROM interaction_event ORDER BY id")
                    .query(String.class).list();
        }
    }

    @Nested
    class Match {

        @Test
        void criaSalaEsperandoComFiltrosEMascote() throws Exception {
            String code = create(jotave, "{\"mediaType\":\"MOVIE\",\"genre\":\"drama\"}");
            assertThat(code).matches("[A-Z2-9]{6}");

            mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + jotave))
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andExpect(jsonPath("$.startedAt", nullValue()))
                    .andExpect(jsonPath("$.mascot", allOf(greaterThanOrEqualTo(0), lessThan(MatchService.MASCOTS))))
                    .andExpect(jsonPath("$.mediaType").value("MOVIE"))
                    .andExpect(jsonPath("$.genre.name").value("Drama"))
                    .andExpect(jsonPath("$.owner.username").value("jotave"))
                    .andExpect(jsonPath("$.participants[*].user.username", contains("jotave")));

            join(bia, code);
            start(jotave, code);
            assertThat(deck(jotave, code)).containsExactlyInAnyOrder(m1, m2, m3);
        }

        @Test
        void generoDesconhecidoDa400() throws Exception {
            mvc.perform(post("/api/match/rooms").header("Authorization", "Bearer " + jotave)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"genre\":\"nao-existe\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void soODonoComecaEComPeloMenosDuasPessoas() throws Exception {
            String code = create(jotave, "{}");
            mvc.perform(post("/api/match/rooms/" + code + "/start").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isConflict());
            join(bia, code);
            mvc.perform(post("/api/match/rooms/" + code + "/start").header("Authorization", "Bearer " + bia))
                    .andExpect(status().isForbidden());

            start(jotave, code)
                    .andExpect(jsonPath("$.status").value("VOTING"))
                    .andExpect(jsonPath("$.startedAt").isNotEmpty());
            start(jotave, code).andExpect(jsonPath("$.status").value("VOTING")); // idempotente
        }

        @Test
        void naoVotaNemVeODeckAntesDeComecar() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            mvc.perform(put("/api/match/rooms/" + code + "/votes/" + m1).header("Authorization", "Bearer " + jotave)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                    .andExpect(status().isConflict());
            mvc.perform(get("/api/match/rooms/" + code + "/deck").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isConflict());
        }

        @Test
        void ninguemNovoEntraDepoisDeComecar() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            start(jotave, code);
            mvc.perform(post("/api/match/rooms/" + code + "/join").header("Authorization", "Bearer " + caio))
                    .andExpect(status().isConflict());
            join(bia, code).andExpect(jsonPath("$.status").value("VOTING")); // quem já estava volta
        }

        @Test
        void donoFechaASalaEnquantoEspera() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            mvc.perform(delete("/api/match/rooms/" + code).header("Authorization", "Bearer " + bia))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/match/rooms/" + code).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + bia))
                    .andExpect(status().isNotFound());
        }

        @Test
        void naoFechaDepoisDeComecar() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            start(jotave, code);
            mvc.perform(delete("/api/match/rooms/" + code).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isConflict());
        }

        @Test
        void todosVeemOMesmoDeckNaMesmaOrdem() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            start(jotave, code);
            assertThat(deck(bia, code)).containsExactlyElementsOf(deck(jotave, code)).hasSize(4);
        }

        @Test
        void deckTiraOQueJaVotei() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            start(jotave, code);
            vote(jotave, code, m2, false);
            assertThat(deck(jotave, code)).doesNotContain(m2).hasSize(3);
        }

        @Test
        void daMatchQuandoTodosCurtemOMesmoTitulo() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            join(caio, code);
            start(jotave, code);
            vote(jotave, code, m1, true);
            vote(bia, code, m1, true);
            vote(caio, code, m2, true);
            vote(caio, code, m1, false).andExpect(jsonPath("$.status").value("VOTING"));
            vote(caio, code, m1, true) // mudou de ideia
                    .andExpect(jsonPath("$.status").value("MATCHED"))
                    .andExpect(jsonPath("$.winner.id").value((int) m1))
                    .andExpect(jsonPath("$.participants[2].votes").value(2))
                    .andExpect(jsonPath("$.participants[2].likes").value(2));

            // depois do match a sala não muda mais
            vote(bia, code, m2, true).andExpect(jsonPath("$.winner.id").value((int) m1));
        }

        @Test
        void desfazerVotoTiraACurtida() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            start(jotave, code);
            vote(jotave, code, m1, true);
            mvc.perform(delete("/api/match/rooms/" + code + "/votes/" + m1).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk());
            vote(bia, code, m1, true).andExpect(jsonPath("$.status").value("VOTING"));
        }

        @Test
        void quemSaiPodeDestravarOMatch() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            join(caio, code);
            start(jotave, code);
            vote(bia, code, m3, true);
            vote(caio, code, m3, true);
            mvc.perform(delete("/api/match/rooms/" + code + "/participants/me").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + bia))
                    .andExpect(jsonPath("$.status").value("MATCHED"))
                    .andExpect(jsonPath("$.winner.id").value((int) m3))
                    .andExpect(jsonPath("$.owner.username").value("bia"));
            mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNotFound());
        }

        @Test
        void donoQueSaiDaSalaEsperandoPassaADonoParaOProximo() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code);
            mvc.perform(delete("/api/match/rooms/" + code + "/participants/me").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + bia))
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andExpect(jsonPath("$.owner.username").value("bia"));
        }

        @Test
        void salaVaziaEApagada() throws Exception {
            String code = create(jotave, "{}");
            mvc.perform(delete("/api/match/rooms/" + code + "/participants/me").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isNoContent());
            assertThat(jdbc.sql("SELECT count(*) FROM match_room").query(Integer.class).single()).isZero();
        }

        @Test
        void quemNaoEstaNaSalaNaoVe() throws Exception {
            String code = create(jotave, "{}");
            mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + bia))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/match/rooms/" + code + "/votes/" + m1).header("Authorization", "Bearer " + bia)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void codigoAceitaMinusculas() throws Exception {
            String code = create(jotave, "{}");
            join(bia, code.toLowerCase()).andExpect(jsonPath("$.participants", hasSize(2)));
        }

        @Test
        void listaSoAsSalasAbertas() throws Exception {
            String done = create(jotave, "{}");
            join(bia, done);
            start(jotave, done);
            vote(jotave, done, m1, true);
            vote(bia, done, m1, true); // deu match: some da lista

            String voting = create(jotave, "{}");
            join(bia, voting);
            start(jotave, voting); // votando: só quem está nela vê

            String waitingJotave = create(jotave, "{}");
            String waitingCaio = create(caio, "{}");

            mvc.perform(get("/api/match/rooms").header("Authorization", "Bearer " + jotave))
                    .andExpect(jsonPath("$[*].code", contains(waitingCaio, waitingJotave, voting)));
            mvc.perform(get("/api/match/rooms").header("Authorization", "Bearer " + caio))
                    .andExpect(jsonPath("$[*].code", contains(waitingCaio, waitingJotave)))
                    .andExpect(jsonPath("$[1].status").value("WAITING"))
                    .andExpect(jsonPath("$[1].owner.username").value("jotave"));
        }
    }

    // ------------------------------------------------------------------ helpers

    @Nested
    class Perfil {

        @Test
        void mostraEmailCorENumeros() throws Exception {
            like(jotave, m1);
            like(jotave, s1);
            mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.username").value("jotave"))
                    .andExpect(jsonPath("$.user.color").value(0))
                    .andExpect(jsonPath("$.user.mascot").value(nullValue()))
                    .andExpect(jsonPath("$.email").value("jotave@exemplo.com"))
                    .andExpect(jsonPath("$.favoriteGenre").value(nullValue()))
                    .andExpect(jsonPath("$.favoriteTitle").value(nullValue()))
                    .andExpect(jsonPath("$.memberSince").isString())
                    .andExpect(jsonPath("$.stats.likes").value(2))
                    .andExpect(jsonPath("$.stats.matches").value(0))
                    .andExpect(jsonPath("$.stats.rooms").value(0))
                    .andExpect(jsonPath("$.stats.votes").value(0));
        }

        @Test
        void atividadeContaCurtidasPorSemanaEMostraAsRecentes() throws Exception {
            like(jotave, m1);
            like(jotave, m2);
            like(jotave, s1);
            // m2 foi curtido há 3 semanas e meia; s1 há 20 semanas (fora do gráfico)
            jdbc.sql("UPDATE title_like SET created_at = now() - interval '25 days' WHERE title_id = :t").param("t", m2).update();
            jdbc.sql("UPDATE title_like SET created_at = now() - interval '140 days' WHERE title_id = :t").param("t", s1).update();
            mvc.perform(get("/api/me/profile/activity").header("Authorization", "Bearer " + jotave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weeklyLikes", hasSize(12)))
                    .andExpect(jsonPath("$.weeklyLikes[11]").value(1))
                    .andExpect(jsonPath("$.weeklyLikes[8]").value(1))
                    .andExpect(jsonPath("$.recentLikes[*].title", contains("Filme Um", "Filme Dois", "Série Um")));
        }

        @Test
        void salvaEApareceParaOsAmigos() throws Exception {
            long drama = jdbc.sql("SELECT id FROM genre WHERE slug = 'drama'").query(Long.class).single();
            mvc.perform(put("/api/me/profile").header("Authorization", "Bearer " + bia)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"displayName":"  Bia   Rocha ","color":6,"mascot":3,
                                     "favoriteGenreId":%d,"favoriteTitleId":%d}""".formatted(drama, s1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.displayName").value("Bia Rocha"))
                    .andExpect(jsonPath("$.user.color").value(6))
                    .andExpect(jsonPath("$.user.mascot").value(3))
                    .andExpect(jsonPath("$.favoriteGenre.slug").value("drama"))
                    .andExpect(jsonPath("$.favoriteTitle.title").value("Série Um"));

            like(bia, m1);
            mvc.perform(get("/api/likes").param("titleIds", String.valueOf(m1)).header("Authorization", "Bearer " + jotave))
                    .andExpect(jsonPath("$[0].friends[0].displayName").value("Bia Rocha"))
                    .andExpect(jsonPath("$[0].friends[0].color").value(6))
                    .andExpect(jsonPath("$[0].friends[0].mascot").value(3));
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bia))
                    .andExpect(jsonPath("$.mascot").value(3));
        }

        @Test
        void nullLimpaOsFavoritosEOBu() throws Exception {
            jdbc.sql("UPDATE app_user SET mascot = 2, favorite_title_id = :t WHERE username = 'caio'").param("t", m1).update();
            mvc.perform(put("/api/me/profile").header("Authorization", "Bearer " + caio)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Caio\",\"color\":1,\"mascot\":null,\"favoriteTitleId\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.mascot").value(nullValue()))
                    .andExpect(jsonPath("$.favoriteTitle").value(nullValue()));
        }

        @Test
        void recusaDadosInvalidos() throws Exception {
            for (String body : List.of(
                    "{\"displayName\":\"   \",\"color\":0}",
                    "{\"displayName\":\"Bia\",\"color\":8}",
                    "{\"displayName\":\"Bia\",\"color\":0,\"mascot\":9}",
                    "{\"displayName\":\"Bia\",\"color\":0,\"favoriteTitleId\":999999}",
                    "{\"displayName\":\"Bia\",\"color\":0,\"favoriteGenreId\":999999}")) {
                mvc.perform(put("/api/me/profile").header("Authorization", "Bearer " + bia)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test
        void exigeLogin() throws Exception {
            mvc.perform(get("/api/me/profile")).andExpect(status().isUnauthorized());
        }
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wannawatch\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private void like(String token, long titleId) throws Exception {
        mvc.perform(put("/api/likes/" + titleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String create(String token, String json) throws Exception {
        String body = mvc.perform(post("/api/match/rooms").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private ResultActions join(String token, String code) throws Exception {
        return mvc.perform(post("/api/match/rooms/" + code + "/join").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions start(String token, String code) throws Exception {
        return mvc.perform(post("/api/match/rooms/" + code + "/start").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions vote(String token, String code, long titleId, boolean liked) throws Exception {
        return mvc.perform(put("/api/match/rooms/" + code + "/votes/" + titleId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":" + liked + "}"))
                .andExpect(status().isOk());
    }

    /** Segue o cursor até o fim (páginas de 2) e devolve os ids. */
    private List<Long> deck(String token, String code) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        do {
            var req = get("/api/match/rooms/" + code + "/deck").param("size", "2").header("Authorization", "Bearer " + token);
            if (cursor != null) req = req.param("cursor", cursor);
            String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.items[*].id");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        return ids;
    }

    private long genre(int ext, String name, String slug) {
        return jdbc.sql("""
                INSERT INTO genre (external_id, name, slug, for_movie, for_series) VALUES (:e, :n, :s, true, true)
                RETURNING id""")
                .param("e", ext).param("n", name).param("s", slug).query(Long.class).single();
    }

    private long title(int ext, String type, String name, long genreId) {
        long id = jdbc.sql("""
                INSERT INTO title (external_id, media_type, title, overview, popularity)
                VALUES (:e, :type, :title, 'Sinopse.', 10) RETURNING id""")
                .param("e", ext).param("type", type).param("title", name).query(Long.class).single();
        jdbc.sql("INSERT INTO title_genre (title_id, genre_id, position) VALUES (:t, :g, 0)")
                .param("t", id).param("g", genreId).update();
        return id;
    }
}
