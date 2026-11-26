package br.com.jotave.wannawatch.match;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deck do Match ordenado pelo gosto do grupo. Catálogo de 13 títulos: 6 de ficção científica,
 * 3 comédias, 3 de terror e 1 drama.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MatchDeckIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired TitleFeatureIndexHolder indexHolder;
    @Autowired RecommendationService recommendations;

    final Map<String, Long> t = new HashMap<>();
    String jotave, bia, caio;

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
        int ext = 1;
        for (int i = 1; i <= 6; i++) t.put("s" + i, title(ext++, "Ficção " + i, 20 + i, scifi));
        for (int i = 1; i <= 3; i++) t.put("c" + i, title(ext++, "Comédia " + i, 40 + i, comedy));
        for (int i = 1; i <= 3; i++) t.put("h" + i, title(ext++, "Terror " + i, 60 + i, horror));
        t.put("d1", title(ext, "Drama 1", 50, drama));
        indexHolder.rebuild();
        recommendations.clearCache();
        jotave = login("jotave");
        bia = login("bia");
        caio = login("caio");
    }

    @Test
    void oQueOsDoisCurtemVemAntesDoQueSoUmCurteEComOMotivo() throws Exception {
        likeAll(jotave, "s1", "s2", "s3", "s4", "c1", "c2");
        likeAll(bia, "s1", "s2", "s3", "s4", "s5");

        String code = startedRoom(jotave, bia);
        List<Long> deck = deck(jotave, code);

        assertThat(deck.indexOf(t.get("s6"))).isLessThan(deck.indexOf(t.get("c3")));
        String json = mvc.perform(get("/api/match/rooms/" + code + "/deck").param("size", "50")
                .header("Authorization", "Bearer " + jotave)).andReturn().getResponse().getContentAsString();
        List<String> s6 = JsonPath.read(json, "$.items[?(@.id == " + t.get("s6") + ")].reason.text");
        assertThat(s6).containsExactly("Bom pra você e pra Bia");
        List<String> c1 = JsonPath.read(json, "$.items[?(@.id == " + t.get("c1") + ")].reason.text");
        assertThat(c1).containsExactly("Você quer ver");
    }

    @Test
    void oQueUmDelesOdeiaVaiProFim() throws Exception {
        likeAll(jotave, "s1", "s2", "h1", "h2", "c1");
        likeAll(bia, "s1", "s2", "s3", "c1", "c2");
        for (String h : List.of("h1", "h2")) signal(bia, t.get(h), "PASS");

        List<Long> deck = deck(jotave, startedRoom(jotave, bia));

        // h3 nunca foi votado, mas é parecido com o que a Bia passou
        for (String ok : List.of("s4", "s5", "s6", "c3")) { // o que os dois toleram
            assertThat(deck.indexOf(t.get("h3"))).as(ok).isGreaterThan(deck.indexOf(t.get(ok)));
        }
    }

    @Test
    void oQueTodoMundoJaViuFicaDeFora() throws Exception {
        signal(jotave, t.get("d1"), "SEEN");
        signal(bia, t.get("d1"), "SEEN");
        signal(bia, t.get("c1"), "SEEN"); // só um viu: continua

        List<Long> deck = deck(jotave, startedRoom(jotave, bia));

        assertThat(deck).doesNotContain(t.get("d1")).contains(t.get("c1")).hasSize(12);
    }

    @Test
    void todosVeemAMesmaOrdemEOSnapshotNaoMudaDepois() throws Exception {
        likeAll(jotave, "s1", "s2");
        String code = startedRoom(jotave, bia);
        List<Long> antes = deck(jotave, code);
        likeAll(bia, "h1", "h2", "h3"); // o gosto muda, a ordem da sala não

        assertThat(deck(bia, code)).isEqualTo(antes);
        assertThat(jdbc.sql("SELECT count(*) FROM match_room_deck").query(Integer.class).single()).isEqualTo(13);
    }

    @Test
    void doisMatchesAoMesmoTempoVenceOQueVemAntesNoDeck() throws Exception {
        String code = startedRoom(jotave, bia, caio);
        List<Long> deck = deck(jotave, code);
        long depois = deck.get(4);
        long antes = deck.get(1);
        for (String u : List.of(jotave, bia)) {
            vote(u, code, depois);
            vote(u, code, antes);
        }
        // o Caio sai sem votar: os dois títulos viram unanimidade de uma vez
        mvc.perform(delete("/api/match/rooms/" + code + "/participants/me").header("Authorization", "Bearer " + caio))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + jotave))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.winner.id").value(antes));
    }

    @Test
    void salaAntigaSemSnapshotContinuaFuncionando() throws Exception {
        String code = startedRoom(jotave, bia);
        long roomId = jdbc.sql("SELECT id FROM match_room WHERE code = :c").param("c", code).query(Long.class).single();
        jdbc.sql("DELETE FROM match_room_deck WHERE room_id = :r").param("r", roomId).update();
        String seed = jdbc.sql("SELECT seed FROM match_room WHERE id = :r").param("r", roomId).query(String.class).single();

        List<Long> deck = deck(jotave, code);
        List<Long> md5 = jdbc.sql("SELECT id FROM title WHERE overview IS NOT NULL ORDER BY md5(:s || ':' || id), id")
                .param("s", seed).query(Long.class).list();
        assertThat(deck).isEqualTo(md5);

        vote(jotave, code, deck.get(3));
        vote(bia, code, deck.get(3));
        mvc.perform(get("/api/match/rooms/" + code).header("Authorization", "Bearer " + bia))
                .andExpect(jsonPath("$.winner.id").value(deck.get(3)));
    }

    // ------------------------------------------------------------------ helpers

    private String startedRoom(String owner, String... others) throws Exception {
        String code = JsonPath.read(mvc.perform(post("/api/match/rooms").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.code");
        for (String o : others) {
            mvc.perform(post("/api/match/rooms/" + code + "/join").header("Authorization", "Bearer " + o))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/match/rooms/" + code + "/start").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        return code;
    }

    /** Segue o cursor até o fim (páginas de 3). */
    private List<Long> deck(String token, String code) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        do {
            var req = get("/api/match/rooms/" + code + "/deck").param("size", "3").header("Authorization", "Bearer " + token);
            if (cursor != null) req = req.param("cursor", cursor);
            String body = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.items[*].id");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        return ids;
    }

    private void vote(String token, String code, long titleId) throws Exception {
        mvc.perform(put("/api/match/rooms/" + code + "/votes/" + titleId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"liked\":true}"))
                .andExpect(status().isOk());
    }

    private void likeAll(String token, String... keys) throws Exception {
        for (String k : keys) {
            mvc.perform(put("/api/likes/" + t.get(k)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }
    }

    private void signal(String token, long titleId, String kind) throws Exception {
        mvc.perform(put("/api/titles/" + titleId + "/signals/" + kind).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wannawatch\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long genre(int ext, String name, String slug) {
        return jdbc.sql("""
                INSERT INTO genre (external_id, name, slug, for_movie, for_series) VALUES (:e, :n, :s, true, true)
                RETURNING id""")
                .param("e", ext).param("n", name).param("s", slug).query(Long.class).single();
    }

    private long title(int ext, String name, double popularity, long genreId) {
        long id = jdbc.sql("""
                INSERT INTO title (external_id, media_type, title, overview, popularity, vote_average, vote_count)
                VALUES (:e, 'MOVIE', :title, 'Sinopse.', :pop, 7.5, 1000) RETURNING id""")
                .param("e", ext).param("title", name).param("pop", popularity).query(Long.class).single();
        jdbc.sql("INSERT INTO title_genre (title_id, genre_id, position) VALUES (:t, :g, 0)")
                .param("t", id).param("g", genreId).update();
        return id;
    }
}
