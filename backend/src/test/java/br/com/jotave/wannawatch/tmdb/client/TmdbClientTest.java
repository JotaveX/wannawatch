package br.com.jotave.wannawatch.tmdb.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.jotave.wannawatch.tmdb.TmdbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmdbClientTest {

    private MockRestServiceServer server;
    private TmdbClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TmdbClient(builder,
                new TmdbProperties("https://api.tmdb.test/3", "segredo", "pt-BR", "BR", 1000, 2, 2));
    }

    @Test
    void enviaTokenEParametrosEConverteSnakeCase() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://api.tmdb.test/3/movie/101")))
                .andExpect(header("Authorization", "Bearer segredo"))
                .andExpect(queryParam("language", "pt-BR"))
                .andExpect(queryParam("append_to_response", TmdbClient.MOVIE_APPEND))
                .andExpect(queryParam("include_video_language", "pt,en,null"))
                .andRespond(withSuccess(TmdbFixtures.raw("movie-101.json"), MediaType.APPLICATION_JSON));

        TmdbDto.MovieDetails movie = client.movie(101);

        assertThat(movie.originalTitle()).isEqualTo("Night on Mars");
        assertThat(movie.watchProviders().results()).containsKey("BR");
        assertThat(movie.releaseDates().results()).hasSize(2);
        server.verify();
    }

    @Test
    void tentaDeNovoQuandoATmdbPedeCalma() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://api.tmdb.test/3/tv/202")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://api.tmdb.test/3/tv/202")))
                .andRespond(withSuccess(TmdbFixtures.raw("tv-202.json"), MediaType.APPLICATION_JSON));

        assertThat(client.tv(202).name()).isEqualTo("Os Vizinhos de Cima");
        server.verify();
    }

    @Test
    void naoTentaDeNovoEm404() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://api.tmdb.test/3/movie/999")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.movie(999))
                .isInstanceOf(TmdbException.class)
                .satisfies(e -> assertThat(((TmdbException) e).isNotFound()).isTrue());
        server.verify();
    }
}
