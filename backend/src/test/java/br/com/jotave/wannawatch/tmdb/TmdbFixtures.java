package br.com.jotave.wannawatch.tmdb;

import br.com.jotave.wannawatch.tmdb.client.TmdbClient;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Lê as respostas gravadas da TMDB em src/test/resources/tmdb. */
public final class TmdbFixtures {

    private TmdbFixtures() {}

    public static String raw(String name) {
        try (InputStream in = stream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T read(String name, Class<T> type) {
        try (InputStream in = stream(name)) {
            return TmdbClient.jsonMapper().readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static TmdbDto.MovieDetails movie101() {
        return read("movie-101.json", TmdbDto.MovieDetails.class);
    }

    public static TmdbDto.TvDetails tv202() {
        return read("tv-202.json", TmdbDto.TvDetails.class);
    }

    private static InputStream stream(String name) {
        InputStream in = TmdbFixtures.class.getResourceAsStream("/tmdb/" + name);
        if (in == null) throw new IllegalArgumentException("Fixture não encontrada: " + name);
        return in;
    }
}
