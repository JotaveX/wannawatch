package br.com.jotave.wannawatch.tmdb.client;

import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Acesso à API v3 da TMDB. Só o back-end fala com a TMDB; o token nunca sai daqui.
 * Respeita um teto de requisições por segundo e tenta de novo em 429 / 5xx.
 */
@Component
public class TmdbClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbClient.class);

    static final String MOVIE_APPEND = "release_dates,videos,credits,translations,watch/providers";
    static final String TV_APPEND = "content_ratings,videos,credits,translations,watch/providers";

    private final RestClient http;
    private final TmdbProperties props;
    private final RequestThrottle throttle;

    public TmdbClient(RestClient.Builder builder, TmdbProperties props) {
        this.props = props;
        this.throttle = new RequestThrottle(props.requestsPerSecond());
        JsonMapper mapper = jsonMapper();
        this.http = builder.clone()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (props.hasToken() ? props.apiToken() : ""))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .messageConverters(converters -> {
                    converters.removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
                    converters.addFirst(new JacksonJsonHttpMessageConverter(mapper));
                })
                .build();
    }

    /** Mapper usado para ler as respostas da TMDB (snake_case, ignora campos desconhecidos). */
    public static JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public TmdbDto.GenreList movieGenres() {
        return get(u -> u.path("/genre/movie/list").queryParam("language", props.language()).build(),
                new ParameterizedTypeReference<>() {});
    }

    public TmdbDto.GenreList tvGenres() {
        return get(u -> u.path("/genre/tv/list").queryParam("language", props.language()).build(),
                new ParameterizedTypeReference<>() {});
    }

    /** Uma página de uma lista (ex.: "/movie/popular", "/trending/all/day"). */
    public TmdbDto.Page<TmdbDto.ListItem> list(String path, int page) {
        return get(u -> u.path(path)
                        .queryParam("language", props.language())
                        .queryParam("page", page)
                        .build(),
                new ParameterizedTypeReference<>() {});
    }

    public TmdbDto.MovieDetails movie(int id) {
        return get(u -> u.path("/movie/{id}")
                        .queryParam("language", props.language())
                        .queryParam("append_to_response", MOVIE_APPEND)
                        .queryParam("include_video_language", videoLanguages())
                        .build(id),
                new ParameterizedTypeReference<>() {});
    }

    public TmdbDto.TvDetails tv(int id) {
        return get(u -> u.path("/tv/{id}")
                        .queryParam("language", props.language())
                        .queryParam("append_to_response", TV_APPEND)
                        .queryParam("include_video_language", videoLanguages())
                        .build(id),
                new ParameterizedTypeReference<>() {});
    }

    /** Trailers em pt e en (com language=pt-BR a TMDB só devolveria vídeos em pt). */
    private String videoLanguages() {
        String lang = props.language().split("-")[0];
        return lang.equals("en") ? "en,null" : lang + ",en,null";
    }

    private <T> T get(Function<UriBuilder, java.net.URI> uri, ParameterizedTypeReference<T> type) {
        int attempt = 0;
        while (true) {
            throttle.acquire();
            try {
                return http.get().uri(uri).retrieve().body(type);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt >= props.maxRetries()) {
                    throw new TmdbException("TMDB respondeu " + status, status, e);
                }
                attempt++;
                Duration wait = retryDelay(e, attempt);
                log.warn("TMDB respondeu {}; tentando de novo em {} ms (tentativa {}/{})",
                        status, wait.toMillis(), attempt, props.maxRetries());
                sleep(wait);
            }
        }
    }

    private static Duration retryDelay(RestClientResponseException e, int attempt) {
        String retryAfter = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // formato de data: cai no backoff exponencial
            }
        }
        return Duration.ofMillis(500L * (1L << (attempt - 1)));
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido esperando a TMDB", ie);
        }
    }
}
