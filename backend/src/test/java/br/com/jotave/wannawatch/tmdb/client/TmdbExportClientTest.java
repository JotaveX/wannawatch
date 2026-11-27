package br.com.jotave.wannawatch.tmdb.client;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TmdbExportClientTest {

    private final TmdbExportClient client = new TmdbExportClient(Clock.systemUTC());

    @Test
    void ficaSoComOsMaisPopularesSemAdultosNemVideos() throws Exception {
        String lines = """
                {"adult":false,"id":1,"original_title":"A","popularity":5.0,"video":false}
                {"adult":true,"id":2,"original_title":"B","popularity":99.0,"video":false}
                {"adult":false,"id":3,"original_title":"C","popularity":50.0,"video":true}
                {"adult":false,"id":4,"original_title":"D","popularity":20.0,"video":false}

                {"adult":false,"id":5,"original_title":"E","popularity":1.0,"video":false}
                {"id":6,"original_name":"Série","popularity":30.0}
                """;

        var top = client.top(new ByteArrayInputStream(lines.getBytes(StandardCharsets.UTF_8)), 3);

        assertThat(top).extracting(TmdbExportClient.Entry::id).containsExactly(6, 4, 1);
    }

    @Test
    void nomeDoArquivoSegueOPadraoDaTmdb() {
        LocalDate day = LocalDate.of(2026, 3, 7);
        assertThat(TmdbExportClient.fileName(MediaType.MOVIE, day)).isEqualTo("movie_ids_03_07_2026.json.gz");
        assertThat(TmdbExportClient.fileName(MediaType.SERIES, day)).isEqualTo("tv_series_ids_03_07_2026.json.gz");
    }
}
