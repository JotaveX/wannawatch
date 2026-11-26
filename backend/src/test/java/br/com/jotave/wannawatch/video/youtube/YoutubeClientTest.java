package br.com.jotave.wannawatch.video.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YoutubeClientTest {

    private MockRestServiceServer server;
    private YoutubeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeClient(builder,
                new YoutubeProperties("https://yt.test/v3", "chave", "BR", "pt", 5));
    }

    @Test
    void buscaVideosCurtosEmbutiveisECodificaABusca() {
        server.expect(once(), requestTo(Matchers.startsWith("https://yt.test/v3/search")))
                .andExpect(queryParam("videoDuration", "short"))
                .andExpect(queryParam("videoEmbeddable", "true"))
                .andExpect(queryParam("type", "video"))
                .andExpect(queryParam("regionCode", "BR"))
                .andExpect(queryParam("maxResults", "5"))
                .andExpect(queryParam("q", "Tom%20%26%20Jerry%20trailer"))
                .andExpect(queryParam("key", "chave"))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"aaa"}},{"id":{"videoId":"bbb"}},{"id":{}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.searchShortVideos("Tom & Jerry trailer")).containsExactly("aaa", "bbb");
        server.verify();
    }

    @Test
    void leDetalhesComDimensoesDoPlayer() {
        server.expect(once(), requestTo(Matchers.startsWith("https://yt.test/v3/videos")))
                .andExpect(queryParam("id", "aaa%2Cbbb"))
                .andExpect(queryParam("maxHeight", "1280"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"aaa",
                          "snippet":{"title":"Trailer","channelTitle":"Estúdio","publishedAt":"2024-01-02T03:04:05Z"},
                          "contentDetails":{"duration":"PT58S","regionRestriction":{"blocked":["BR"]}},
                          "status":{"embeddable":true,"privacyStatus":"public"},
                          "player":{"embedWidth":"720","embedHeight":"1280"}}]}""",
                        MediaType.APPLICATION_JSON));

        List<YoutubeDto.Video> videos = client.videos(List.of("aaa", "bbb"));

        assertThat(videos).hasSize(1);
        YoutubeDto.Video v = videos.getFirst();
        assertThat(v.snippet().channelTitle()).isEqualTo("Estúdio");
        assertThat(v.player().embedHeight()).isEqualTo(1280L);
        assertThat(v.contentDetails().regionRestriction().blocked()).containsExactly("BR");
        server.verify();
    }

    @Test
    void avisaQuandoACotaAcaba() {
        server.expect(once(), requestTo(Matchers.startsWith("https://yt.test/v3/search")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"code":403,"errors":[{"reason":"quotaExceeded"}]}}"""));

        assertThatThrownBy(() -> client.searchShortVideos("Duna trailer"))
                .isInstanceOfSatisfying(YoutubeException.class, e -> {
                    assertThat(e.quotaExceeded()).isTrue();
                    assertThat(e.getMessage()).contains("cota").doesNotContain("chave");
                });
    }

    @Test
    void semChaveNaoChamaAApi() {
        YoutubeClient semChave = new YoutubeClient(RestClient.builder(),
                new YoutubeProperties("https://yt.test/v3", "", "BR", "pt", 5));
        assertThat(semChave.enabled()).isFalse();
        assertThatThrownBy(() -> semChave.searchShortVideos("x")).isInstanceOf(YoutubeException.class);
    }
}
