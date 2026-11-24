package br.com.jotave.wannawatch.tmdb.importer;

import java.time.Instant;
import java.util.Map;

public record ImportResult(
        Status status,
        Instant startedAt,
        Instant finishedAt,
        int genres,
        int titlesSaved,
        int titlesSkipped,
        int titlesFailed,
        Map<String, Integer> listSizes,
        String message) {

    public enum Status { SUCCESS, PARTIAL, SKIPPED, FAILED }
}
