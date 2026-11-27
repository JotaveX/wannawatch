package br.com.jotave.wannawatch.profile.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Foto de perfil de ponta a ponta, com o armazenamento em disco (o mesmo contrato do R2). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProfilePhotoApiIntegrationTest {

    static final String URL = "http://localhost/fotos/avatars/%d/[0-9a-f-]{36}\\.jpg";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Value("${wannawatch.photos.local.dir}") Path dir;

    String token;
    long userId;

    @BeforeEach
    void login() throws Exception {
        jdbc.sql("UPDATE app_user SET photo_key = NULL, photo_url = NULL").update();
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bia\",\"password\":\"wannawatch\"}"))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.token");
        userId = ((Number) JsonPath.read(body, "$.user.id")).longValue();
    }

    @Test
    void enviaFotoGuardaJpegEOUsuarioPassaATrazerAUrl() throws Exception {
        String body = upload(png()).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.photoUrl", matchesPattern(URL.formatted(userId))))
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(body, "$.user.photoUrl");
        Path file = fileOf(url);
        assertThat(file).exists();
        assertThat(Files.readAllBytes(file)).startsWith(0xFF, 0xD8);

        // o UserRef de qualquer lugar traz a foto: o login dos amigos, o /me...
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.photoUrl").value(url));
        // e o próprio back serve o arquivo no modo em disco
        mvc.perform(get(url.replace("http://localhost", "")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void trocarAFotoApagaAAnterior() throws Exception {
        Path first = fileOf(JsonPath.read(upload(png()).andReturn().getResponse().getContentAsString(),
                "$.user.photoUrl"));
        String second = JsonPath.read(upload(png()).andReturn().getResponse().getContentAsString(),
                "$.user.photoUrl");

        assertThat(first).doesNotExist();
        assertThat(fileOf(second)).exists();
    }

    @Test
    void removerAFotoApagaOArquivoELimpaAUrl() throws Exception {
        Path file = fileOf(JsonPath.read(upload(png()).andReturn().getResponse().getContentAsString(),
                "$.user.photoUrl"));

        mvc.perform(delete("/api/me/profile/photo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.photoUrl").value(nullValue()));

        assertThat(file).doesNotExist();
        assertThat(jdbc.sql("SELECT photo_key FROM app_user WHERE id = :id").param("id", userId)
                .query(String.class).optional()).isEmpty();
    }

    @Test
    void arquivoQueNaoEImagemDa400() throws Exception {
        upload(new MockMultipartFile("file", "x.png", "image/png", "nada".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Não deu pra abrir essa imagem. Envie um JPG ou PNG."));
    }

    @Test
    void semLoginDa401() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/me/profile/photo").file(png()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions upload(MockMultipartFile file) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/me/profile/photo").file(file)
                .header("Authorization", "Bearer " + token));
    }

    private static MockMultipartFile png() throws Exception {
        return new MockMultipartFile("file", "foto.png", "image/png",
                PhotoImagesTest.encode(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png"));
    }

    private Path fileOf(String url) {
        return dir.resolve(url.substring("http://localhost/fotos/".length()));
    }
}
