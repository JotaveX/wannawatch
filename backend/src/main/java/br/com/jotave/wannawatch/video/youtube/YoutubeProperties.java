package br.com.jotave.wannawatch.video.youtube;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey            chave da YouTube Data API v3. Vem do .env, nunca do código.
 * @param regionCode        país dos resultados e da checagem de bloqueio regional.
 * @param relevanceLanguage idioma preferido nos resultados da busca.
 * @param resultsPerSearch  quantos vídeos cada busca traz (a busca custa a mesma cota com 1 ou 50).
 */
@ConfigurationProperties("wannawatch.youtube")
public record YoutubeProperties(
        String baseUrl,
        String apiKey,
        String regionCode,
        String relevanceLanguage,
        int resultsPerSearch) {

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
