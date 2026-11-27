package br.com.jotave.wannawatch.video.youtube;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Acesso à YouTube Data API v3, só para achar vídeos e ler metadados. Os vídeos nunca são
 * baixados: tocam pelo player oficial (os termos da API proíbem guardar o conteúdo).
 *
 * <p>Cota padrão: 100 buscas por dia; {@code videos.list} custa 1 unidade por chamada.
 * A chave vai como query param, então nunca logamos a URL.
 */
@Component
public class YoutubeClient {

    /** Altura máxima pedida ao player: o YouTube devolve largura/altura na proporção real do vídeo. */
    static final int PLAYER_MAX_HEIGHT = 1280;

    private final RestClient http;
    private final YoutubeProperties props;

    public YoutubeClient(RestClient.Builder builder, YoutubeProperties props) {
        this.props = props;
        JsonMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.http = builder.clone()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .messageConverters(converters -> {
                    converters.removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
                    converters.addFirst(new JacksonJsonHttpMessageConverter(mapper));
                })
                .build();
    }

    public boolean enabled() {
        return props.hasKey();
    }

    /** Ids dos vídeos curtos (menos de 4 min) e embutíveis que a busca devolve, na ordem dela. */
    public List<String> searchShortVideos(String query) {
        YoutubeDto.SearchResponse res = call(() -> http.get()
                .uri(u -> u.path("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("videoDuration", "short")
                        .queryParam("videoEmbeddable", "true")
                        .queryParam("maxResults", props.resultsPerSearch())
                        .queryParam("regionCode", props.regionCode())
                        .queryParam("relevanceLanguage", props.relevanceLanguage())
                        .queryParam("fields", "items(id/videoId)")
                        .queryParam("q", "{q}")
                        .queryParam("key", "{key}")
                        // variáveis são codificadas por inteiro ("Tom & Jerry" não quebra a URL)
                        .build(Map.of("q", query, "key", props.apiKey())))
                .retrieve()
                .body(YoutubeDto.SearchResponse.class));
        if (res == null || res.items() == null) return List.of();
        return res.items().stream()
                .map(YoutubeDto.SearchItem::id)
                .filter(id -> id != null && id.videoId() != null)
                .map(YoutubeDto.SearchId::videoId)
                .distinct()
                .toList();
    }

    /** Detalhes de até 50 vídeos numa chamada (1 unidade de cota). */
    public List<YoutubeDto.Video> videos(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        YoutubeDto.VideoListResponse res = call(() -> http.get()
                .uri(u -> u.path("/videos")
                        .queryParam("part", "snippet,contentDetails,status,player")
                        .queryParam("maxHeight", PLAYER_MAX_HEIGHT)
                        .queryParam("fields", "items(id,snippet(title,channelTitle,publishedAt),"
                                + "contentDetails(duration,regionRestriction),status(embeddable,privacyStatus),"
                                + "player(embedWidth,embedHeight))")
                        .queryParam("id", "{ids}")
                        .queryParam("key", "{key}")
                        .build(Map.of("ids", String.join(",", ids), "key", props.apiKey())))
                .retrieve()
                .body(YoutubeDto.VideoListResponse.class));
        return res == null || res.items() == null ? List.of() : res.items();
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        if (!props.hasKey()) {
            throw new YoutubeException("YOUTUBE_API_KEY não configurada.", 0, false, null);
        }
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // a cota estourada vem como 403 quotaExceeded, mas no fim do dia às vezes chega como 429
            boolean quota = status == 429
                    || (status == 403 && e.getResponseBodyAsString().contains("quotaExceeded"));
            String message = quota
                    ? "A cota diária da API do YouTube acabou. Ela volta à meia-noite (horário do Pacífico)."
                    : "A API do YouTube respondeu " + status + ".";
            throw new YoutubeException(message, status, quota, e);
        }
    }
}
