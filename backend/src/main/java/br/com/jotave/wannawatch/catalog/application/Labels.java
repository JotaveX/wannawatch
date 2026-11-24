package br.com.jotave.wannawatch.catalog.application;

import java.util.Map;

/** Tradução dos termos que a TMDB manda em inglês. */
final class Labels {

    private Labels() {}

    private static final Map<String, String> JOBS = Map.of(
            "Creator", "Criação",
            "Director", "Direção",
            "Screenplay", "Roteiro",
            "Writer", "Roteiro",
            "Story", "História",
            "Novel", "Livro",
            "Original Music Composer", "Trilha sonora",
            "Director of Photography", "Fotografia");

    private static final Map<String, String> STATUS = Map.ofEntries(
            Map.entry("Released", "Lançado"),
            Map.entry("Returning Series", "Em andamento"),
            Map.entry("Ended", "Finalizada"),
            Map.entry("Canceled", "Cancelada"),
            Map.entry("In Production", "Em produção"),
            Map.entry("Post Production", "Em pós-produção"),
            Map.entry("Planned", "Planejado"),
            Map.entry("Rumored", "Rumor"),
            Map.entry("Pilot", "Piloto"));

    static String job(String job) {
        return job == null ? null : JOBS.getOrDefault(job, job);
    }

    static String status(String status) {
        return status == null ? null : STATUS.getOrDefault(status, status);
    }
}
