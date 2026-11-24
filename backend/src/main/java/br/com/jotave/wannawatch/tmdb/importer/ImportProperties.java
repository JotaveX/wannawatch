package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param onStartupIfEmpty importa ao subir a aplicação se o banco não tiver nenhum título.
 * @param cron             agenda do job (formato Spring). "-" desliga.
 * @param adminToken       token do header X-Admin-Token do endpoint de admin. Vazio = endpoint desligado.
 * @param lists            listas da TMDB a importar.
 * @param genreNames       nomes nossos para gêneros que a TMDB não traduz (id da TMDB → nome).
 */
@ConfigurationProperties("wannawatch.import")
public record ImportProperties(
        boolean onStartupIfEmpty,
        String cron,
        String zone,
        String adminToken,
        List<ListSpec> lists,
        Map<Integer, String> genreNames) {

    public ImportProperties {
        genreNames = genreNames == null ? Map.of() : Map.copyOf(genreNames);
    }

    /**
     * @param key       nome da lista no nosso banco (title_ranking.list_key).
     * @param path      caminho na TMDB, ex.: "/movie/popular".
     * @param mediaType tipo fixo da lista; nulo quando a lista mistura (trending/all).
     * @param pages     quantas páginas de 20 itens buscar.
     */
    public record ListSpec(String key, String path, MediaType mediaType, int pages) {}
}
