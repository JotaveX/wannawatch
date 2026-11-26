package br.com.jotave.wannawatch.recommendation.domain;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Monta títulos e índices para os testes do motor (sem Spring nem banco). */
final class TitleBuilder {

    private final long id;
    private MediaType type = MediaType.MOVIE;
    private String title;
    private String language = "en";
    private Integer year = 2020;
    private final List<Long> genres = new ArrayList<>();
    private final List<Integer> keywords = new ArrayList<>();
    private final List<TitleFeatures.Credit> credits = new ArrayList<>();
    private double popularity = 10;
    private Double voteAverage = 7.0;
    private Integer voteCount = 1000;
    private final List<Long> recommended = new ArrayList<>();

    private TitleBuilder(long id) {
        this.id = id;
        this.title = "Título " + id;
    }

    static TitleBuilder title(long id) {
        return new TitleBuilder(id);
    }

    TitleBuilder named(String name) {
        this.title = name;
        return this;
    }

    TitleBuilder series() {
        this.type = MediaType.SERIES;
        return this;
    }

    TitleBuilder language(String language) {
        this.language = language;
        return this;
    }

    TitleBuilder year(Integer year) {
        this.year = year;
        return this;
    }

    TitleBuilder genres(long... ids) {
        for (long g : ids) genres.add(g);
        return this;
    }

    TitleBuilder keywords(int... ids) {
        for (int k : ids) keywords.add(k);
        return this;
    }

    TitleBuilder director(long personId) {
        credits.add(new TitleFeatures.Credit(personId, CreditRole.DIRECTION, 0));
        return this;
    }

    TitleBuilder cast(long personId, int order) {
        credits.add(new TitleFeatures.Credit(personId, CreditRole.CAST, order));
        return this;
    }

    TitleBuilder popularity(double popularity) {
        this.popularity = popularity;
        return this;
    }

    TitleBuilder votes(double average, int count) {
        this.voteAverage = average;
        this.voteCount = count;
        return this;
    }

    TitleBuilder recommends(long... ids) {
        for (long r : ids) recommended.add(r);
        return this;
    }

    TitleFeatures build() {
        return new TitleFeatures(id, type, title, language, year, genres, keywords, credits, popularity,
                voteAverage, voteCount, recommended);
    }

    static TitleFeatureIndex index(TitleBuilder... titles) {
        List<TitleFeatures> list = new ArrayList<>();
        for (TitleBuilder t : titles) list.add(t.build());
        Map<Long, String> genreNames = new HashMap<>(Map.of(1L, "Ficção científica", 2L, "Drama", 3L, "Comédia",
                4L, "Terror", 5L, "Animação"));
        Map<Long, String> people = new HashMap<>(Map.of(100L, "Christopher Nolan", 200L, "Pedro Pascal",
                300L, "Greta Gerwig"));
        return TitleFeatureIndex.build(list, FeatureWeights.defaults(), 0.1, 200, genreNames, people);
    }
}
