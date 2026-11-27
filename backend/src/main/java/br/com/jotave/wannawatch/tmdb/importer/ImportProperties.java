package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param onStartupIfEmpty importa ao subir a aplicação se o banco não tiver nenhum título.
 * @param cron             agenda do job (formato Spring). "-" desliga.
 * @param adminToken       token do header X-Admin-Token do endpoint de admin. Vazio = endpoint desligado.
 * @param lists            listas da TMDB a importar.
 * @param genreNames       nomes nossos para gêneros que a TMDB não traduz (id da TMDB → nome).
 * @param export           carga do catálogo grande a partir do export diário de ids da TMDB.
 * @param search           busca na TMDB quando a busca daqui acha pouco.
 */
@ConfigurationProperties("wannawatch.import")
public record ImportProperties(
        boolean onStartupIfEmpty,
        String cron,
        String zone,
        String adminToken,
        List<ListSpec> lists,
        Map<Integer, String> genreNames,
        Export export,
        Search search) {

    public ImportProperties {
        genreNames = genreNames == null ? Map.of() : Map.copyOf(genreNames);
        if (export == null) export = new Export(null, null, 0, 0, 0);
        if (search == null) search = new Search(false, 0, 0, null, 0);
    }

    /**
     * Os títulos mais populares do export diário da TMDB (arquivo com todos os ids, sem limite de
     * páginas). Só busca os que ainda não estão no banco, então rodar de novo é barato e continua
     * de onde parou. Os títulos entram só para a busca ({@code in_feeds = false}).
     *
     * @param baseUrl   onde ficam os arquivos (https://files.tmdb.org/p/exports).
     * @param cron      agenda (formato Spring). "-" desliga; o endpoint de admin dispara na hora.
     * @param maxMovies quantos filmes manter, os mais populares primeiro.
     * @param maxSeries quantas séries manter, as mais populares primeiro.
     * @param batchSize quantos títulos buscar antes de gravar (limita a memória).
     */
    public record Export(String baseUrl, String cron, int maxMovies, int maxSeries, int batchSize) {
        public Export {
            if (batchSize <= 0) batchSize = 100;
        }
    }

    /**
     * Quando a busca daqui acha menos de {@code minTitleHits} títulos pelo nome, pergunta à TMDB e
     * importa os primeiros resultados (só para a busca). A mesma consulta não vai à TMDB de novo
     * dentro de {@code cacheTtl}, e há um teto de consultas por minuto (a busca é aberta a visitantes).
     *
     * @param enabled         liga a busca na TMDB.
     * @param minTitleHits    abaixo disso (na primeira página) a TMDB é consultada.
     * @param maxTitles       quantos resultados da TMDB importar por consulta.
     * @param cacheTtl        por quanto tempo uma consulta já feita não vai à TMDB de novo.
     * @param maxPerMinute    teto de consultas à TMDB por minuto, somando todo mundo.
     */
    public record Search(boolean enabled, int minTitleHits, int maxTitles, Duration cacheTtl, int maxPerMinute) {
        public Search {
            if (cacheTtl == null) cacheTtl = Duration.ofHours(12);
        }
    }

    /**
     * @param key       nome da lista no nosso banco (title_ranking.list_key).
     * @param path      caminho na TMDB, ex.: "/movie/popular".
     * @param mediaType tipo fixo da lista; nulo quando a lista mistura (trending/all).
     * @param pages     quantas páginas de 20 itens buscar.
     */
    public record ListSpec(String key, String path, MediaType mediaType, int pages) {}
}
