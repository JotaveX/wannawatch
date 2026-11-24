package br.com.jotave.wannawatch.tmdb.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiToken          "API Read Access Token" (Bearer). Vem do .env, nunca do código.
 * @param requestsPerSecond teto de requisições por segundo que o importador faz.
 * @param maxConcurrency    quantas chamadas de detalhe podem estar em andamento ao mesmo tempo.
 * @param maxRetries        novas tentativas em 429 / 5xx.
 */
@ConfigurationProperties("wannawatch.tmdb")
public record TmdbProperties(
        String baseUrl,
        String apiToken,
        String language,
        String region,
        int requestsPerSecond,
        int maxConcurrency,
        int maxRetries) {

    public boolean hasToken() {
        return apiToken != null && !apiToken.isBlank();
    }
}
