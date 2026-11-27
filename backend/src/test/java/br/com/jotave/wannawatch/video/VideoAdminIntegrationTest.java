package br.com.jotave.wannawatch.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import br.com.jotave.wannawatch.video.youtube.YoutubeClient;
import br.com.jotave.wannawatch.video.youtube.YoutubeDto;
import br.com.jotave.wannawatch.video.youtube.YoutubeException;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Busca de vídeos no YouTube (cliente mockado) e revisão por admin, contra um Postgres real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VideoAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean YoutubeClient youtube;

    long duna;
    long serie;
    String admin;

    @BeforeEach
    void seed() throws Exception {
        jdbc.sql("TRUNCATE title, genre, person RESTART IDENTITY CASCADE").update();
        duna = insert(1, "MOVIE", "Duna", "2021-09-15", 50);
        serie = insert(2, "SERIES", "Round 6", null, 40);
        insert(3, "MOVIE", "Pouco popular", null, 1);
        when(youtube.enabled()).thenReturn(true);
        jdbc.sql("UPDATE app_user SET is_admin = (username = 'jotave')").update();
        admin = login("jotave");
    }

    @AfterEach
    void noAdmins() {
        jdbc.sql("UPDATE app_user SET is_admin = FALSE").update();
    }

    @Test
    void buscaOsMaisPopularesEGuardaSoOsVerticais() throws Exception {
        when(youtube.searchShortVideos("Duna trailer #shorts")).thenReturn(List.of("v1", "v2", "v3"));
        when(youtube.searchShortVideos("Round 6 série trailer #shorts")).thenReturn(List.of());
        when(youtube.videos(List.of("v1", "v2", "v3"))).thenReturn(List.of(
                video("v1", 720L, 1280L, "PT45S", null),
                video("v2", 1280L, 720L, "PT2M10S", null),
                video("v3", 720L, 1280L, "PT30S", new YoutubeDto.RegionRestriction(List.of("US"), null))));
        when(youtube.videos(List.of())).thenReturn(List.of());

        mvc.perform(discover("{\"limit\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titlesSearched").value(2))
                .andExpect(jsonPath("$.videosFound").value(2))
                .andExpect(jsonPath("$.horizontalSkipped").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());

        mvc.perform(get("/api/admin/videos").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[0].externalKey").value("v1"))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[0].vertical").value(true))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[0].durationSeconds").value(45))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[0].status").value("PENDING"))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[1].externalKey").value("v3"))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[1].blockedInRegion").value(true))
                .andExpect(jsonPath("$[?(@.title == 'Round 6')].resultsCount").value(0));

        // a próxima rodada pula quem já foi buscado
        when(youtube.searchShortVideos("Pouco popular trailer #shorts")).thenReturn(List.of());
        mvc.perform(discover("{\"limit\":5}")).andExpect(jsonPath("$.titlesSearched").value(1));
    }

    @Test
    void aprovaEReprovaVideos() throws Exception {
        when(youtube.searchShortVideos(anyString())).thenReturn(List.of("v1"));
        when(youtube.videos(anyList())).thenReturn(List.of(video("v1", 720L, 1280L, "PT30S", null)));
        mvc.perform(discover("{\"titleIds\":[" + duna + "]}")).andExpect(status().isOk());
        long id = jdbc.sql("SELECT id FROM title_video WHERE title_id = :t").param("t", duna)
                .query(Long.class).single();

        mvc.perform(patch("/api/admin/videos/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT status FROM title_video WHERE id = :id").param("id", id)
                .query(String.class).single()).isEqualTo("APPROVED");
        mvc.perform(get("/api/admin/videos").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[?(@.title == 'Duna')].videos[0].reviewedAt").isNotEmpty());

        // voltar para pendente limpa a data da revisão
        mvc.perform(patch("/api/admin/videos/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT reviewed_at IS NULL FROM title_video WHERE id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue();

        mvc.perform(patch("/api/admin/videos/999999").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscaAvancadaFiltraTipoAnoEPulaQuemTemVideoAprovado() throws Exception {
        when(youtube.searchShortVideos(anyString())).thenReturn(List.of());
        when(youtube.videos(anyList())).thenReturn(List.of());

        // só séries
        mvc.perform(discover("{\"limit\":5,\"type\":\"SERIES\"}"))
                .andExpect(jsonPath("$.titlesSearched").value(1));
        verify(youtube).searchShortVideos("Round 6 série trailer #shorts");

        // por ano de lançamento (só Duna é de 2021)
        mvc.perform(discover("{\"limit\":5,\"yearFrom\":2020,\"yearTo\":2022}"))
                .andExpect(jsonPath("$.titlesSearched").value(1));
        verify(youtube).searchShortVideos("Duna trailer #shorts");

        // ano inicial depois do final
        mvc.perform(discover("{\"yearFrom\":2024,\"yearTo\":2020}")).andExpect(status().isBadRequest());

        // Duna ganha um vídeo aprovado: buscando de novo os já buscados, ela fica de fora...
        jdbc.sql("""
                INSERT INTO title_video (title_id, source, platform, external_key, status)
                VALUES (:t, 'YOUTUBE_SEARCH', 'YOUTUBE', 'aprovado123', 'APPROVED')""")
                .param("t", duna).update();
        mvc.perform(discover("{\"limit\":5,\"includeSearched\":true}"))
                .andExpect(jsonPath("$.titlesSearched").value(2));
        // ...a não ser que a opção de pular esteja desligada
        mvc.perform(discover("{\"limit\":5,\"includeSearched\":true,\"skipApproved\":false}"))
                .andExpect(jsonPath("$.titlesSearched").value(3));
    }

    @Test
    void paraQuandoACotaAcabaEDevolveOQueJaFez() throws Exception {
        when(youtube.searchShortVideos("Duna trailer #shorts")).thenReturn(List.of());
        when(youtube.videos(List.of())).thenReturn(List.of());
        when(youtube.searchShortVideos("Round 6 série trailer #shorts"))
                .thenThrow(new YoutubeException("A cota diária da API do YouTube acabou.", 403, true, null));

        mvc.perform(discover("{\"limit\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titlesSearched").value(1))
                .andExpect(jsonPath("$.error").value("A cota diária da API do YouTube acabou."))
                .andExpect(jsonPath("$.quotaExceeded").value(true));
        assertThat(jdbc.sql("SELECT count(*) FROM title_video_search").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void buscaAutomaticaUsaOAdminToken() throws Exception {
        when(youtube.searchShortVideos(anyString())).thenReturn(List.of());
        when(youtube.videos(List.of())).thenReturn(List.of());

        mvc.perform(post("/api/admin/videos/discover/auto")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/videos/discover/auto").header("X-Admin-Token", "errado"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/videos/discover/auto").header("Authorization", "Bearer " + admin))
                .andExpect(status().isUnauthorized());
        verify(youtube, never()).searchShortVideos(anyString());

        mvc.perform(post("/api/admin/videos/discover/auto").header("X-Admin-Token", "test-admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titlesSearched").value(3))
                .andExpect(jsonPath("$.quotaExceeded").value(false));
        // tudo já buscado: a próxima rodada volta vazia (é assim que o job sabe que acabou)
        mvc.perform(post("/api/admin/videos/discover/auto").header("X-Admin-Token", "test-admin"))
                .andExpect(jsonPath("$.titlesSearched").value(0));
    }

    @Test
    void soAdministradorEntra() throws Exception {
        mvc.perform(get("/api/admin/videos")).andExpect(status().isUnauthorized());
        String bia = login("bia");
        mvc.perform(post("/api/admin/videos/discover").header("Authorization", "Bearer " + bia))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/videos").header("Authorization", "Bearer " + bia))
                .andExpect(status().isForbidden());
        verify(youtube, never()).searchShortVideos(anyString());

        mvc.perform(get("/api/auth/account").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.account.admin").value(true));
        mvc.perform(get("/api/auth/account").header("Authorization", "Bearer " + bia))
                .andExpect(jsonPath("$.account.admin").value(false));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wannawatch\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private org.springframework.test.web.servlet.RequestBuilder discover(String json) {
        return post("/api/admin/videos/discover").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private long insert(int external, String type, String title, String release, double popularity) {
        return jdbc.sql("""
                INSERT INTO title (external_id, media_type, title, release_date, popularity)
                VALUES (:e, :type, :t, CAST(:r AS DATE), :p) RETURNING id""")
                .param("e", external).param("type", type).param("t", title).param("r", release)
                .param("p", popularity).query(Long.class).single();
    }

    private static YoutubeDto.Video video(String id, Long w, Long h, String duration,
                                          YoutubeDto.RegionRestriction region) {
        return new YoutubeDto.Video(id,
                new YoutubeDto.Snippet("Trailer " + id, "Canal", Instant.parse("2024-01-01T00:00:00Z")),
                new YoutubeDto.ContentDetails(duration, region),
                new YoutubeDto.Status(true, "public"),
                new YoutubeDto.Player(w, h));
    }
}
