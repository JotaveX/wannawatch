package br.com.jotave.wannawatch.catalog.domain;

import java.util.List;

/**
 * Dados só de exibição, guardados na coluna JSONB {@code title.details}.
 * Nada aqui é usado em filtro ou ordenação.
 */
public record TitleDetails(
        List<NamedLogo> productionCompanies,
        List<Country> productionCountries,
        List<NamedLogo> networks,
        WatchProviders watchProviders) {

    public static TitleDetails empty() {
        return new TitleDetails(List.of(), List.of(), List.of(), WatchProviders.none());
    }

    public record NamedLogo(String name, String logoPath) {}

    public record Country(String code, String name) {}

    /** Onde assistir no Brasil (dados da JustWatch via TMDB). */
    public record WatchProviders(
            String link,
            List<NamedLogo> streaming,
            List<NamedLogo> free,
            List<NamedLogo> ads,
            List<NamedLogo> rent,
            List<NamedLogo> buy) {

        public static WatchProviders none() {
            return new WatchProviders(null, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}
