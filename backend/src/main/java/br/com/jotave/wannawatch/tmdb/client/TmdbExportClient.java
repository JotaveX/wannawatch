package br.com.jotave.wannawatch.tmdb.client;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê o export diário da TMDB: um .json.gz com uma linha por título (id, popularidade, adulto...),
 * publicado todo dia por volta das 8h UTC em {@code {base}/movie_ids_MM_DD_YYYY.json.gz} e
 * {@code tv_series_ids_...}. Não precisa de token nem conta no limite de requisições da API.
 * O arquivo de filmes tem mais de um milhão de linhas: é lido em streaming e só os N mais
 * populares ficam na memória.
 */
@Component
public class TmdbExportClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbExportClient.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("MM_dd_yyyy");

    /** Uma linha do export. Filmes têm adult e video; séries não. */
    public record Entry(Integer id, Boolean adult, Boolean video, Double popularity) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final JsonMapper json = TmdbClient.jsonMapper();
    private final Clock clock;

    public TmdbExportClient(Clock clock) {
        this.clock = clock;
    }

    /**
     * Os {@code limit} títulos mais populares do export mais recente (ontem; se ainda não saiu,
     * anteontem), sem adultos nem "vídeos" (making of, shows gravados), do mais popular ao menos.
     */
    public List<Entry> mostPopular(String baseUrl, MediaType type, int limit) {
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
        TmdbException last = null;
        for (LocalDate day : List.of(yesterday, yesterday.minusDays(1))) {
            URI uri = URI.create(baseUrl + "/" + fileName(type, day));
            try {
                return read(uri, limit);
            } catch (TmdbException e) {
                if (!e.isNotFound()) throw e;
                last = e;
                log.info("Export {} ainda não existe; tentando o do dia anterior", uri);
            }
        }
        throw last;
    }

    static String fileName(MediaType type, LocalDate day) {
        String prefix = type == MediaType.MOVIE ? "movie_ids_" : "tv_series_ids_";
        return prefix + FILE_DATE.format(day) + ".json.gz";
    }

    private List<Entry> read(URI uri, int limit) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                // a TMDB responde 403 (S3) quando o arquivo do dia ainda não existe
                int status = response.statusCode();
                if (status == 403 || status == 404) {
                    throw new TmdbException("Export não encontrado: " + uri, 404, null);
                }
                if (status != 200) {
                    throw new TmdbException("Export respondeu " + status + ": " + uri, status, null);
                }
                return top(new GZIPInputStream(body), limit);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o export " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido lendo o export", e);
        }
    }

    /** Lê as linhas e guarda só as {@code limit} de maior popularidade (heap de tamanho fixo). */
    List<Entry> top(InputStream jsonLines, int limit) throws IOException {
        if (limit <= 0) return List.of();
        Comparator<Entry> byPopularity = Comparator.comparingDouble(Entry::popularity);
        PriorityQueue<Entry> heap = new PriorityQueue<>(limit, byPopularity);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jsonLines, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Entry e = json.readValue(line, Entry.class);
                if (e.id() == null || e.popularity() == null) continue;
                if (Boolean.TRUE.equals(e.adult()) || Boolean.TRUE.equals(e.video())) continue;
                if (heap.size() < limit) {
                    heap.add(e);
                } else if (e.popularity() > heap.peek().popularity()) {
                    heap.poll();
                    heap.add(e);
                }
            }
        }
        List<Entry> result = new ArrayList<>(heap);
        result.sort(byPopularity.reversed());
        return result;
    }
}
