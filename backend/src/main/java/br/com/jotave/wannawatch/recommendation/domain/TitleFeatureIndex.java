package br.com.jotave.wannawatch.recommendation.domain;

import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Catálogo inteiro em memória, imutável: vetor de conteúdo, popularidade e qualidade de cada título,
 * mais os nomes de gêneros e pessoas (para os motivos). Para atualizar, monte outro e troque a referência.
 */
public final class TitleFeatureIndex {

    public static final TitleFeatureIndex EMPTY = new TitleFeatureIndex(Map.of(), Map.of(), Map.of(), 0);

    /**
     * @param popularity percentil de log(1 + popularidade) no catálogo, em [0, 1]
     * @param quality    média bayesiana da nota, normalizada no catálogo, em [0, 1]
     * @param roles      papel principal de cada pessoa neste título
     */
    public record Entry(TitleFeatures features, SparseVector vector, double popularity, double quality,
                        Map<Long, CreditRole> roles, Set<Long> recommended) {

        public long id() {
            return features.id();
        }

        public Long primaryGenreId() {
            return features.primaryGenreId();
        }

        public MediaType mediaType() {
            return features.mediaType();
        }

        public boolean hasGenre(long genreId) {
            return features.genreIds().contains(genreId);
        }
    }

    private final Map<Long, Entry> entries;
    private final Map<Long, String> genreNames;
    private final Map<Long, String> personNames;
    private final double recommendationBonus;

    private TitleFeatureIndex(Map<Long, Entry> entries, Map<Long, String> genreNames,
                              Map<Long, String> personNames, double recommendationBonus) {
        this.entries = entries;
        this.genreNames = genreNames;
        this.personNames = personNames;
        this.recommendationBonus = recommendationBonus;
    }

    /**
     * @param recommendationBonus somado à similaridade quando a TMDB recomenda um a partir do outro
     * @param qualityPriorVotes   "m" da média bayesiana: quantos votos a nota média do catálogo vale
     */
    public static TitleFeatureIndex build(List<TitleFeatures> titles, FeatureWeights weights,
                                          double recommendationBonus, int qualityPriorVotes,
                                          Map<Long, String> genreNames, Map<Long, String> personNames) {
        if (titles.isEmpty()) return EMPTY;
        Map<Long, SparseVector> vectors = new FeatureVectorizer(weights).vectorize(titles);

        Map<Long, Double> logPop = new HashMap<>();
        titles.forEach(t -> logPop.put(t.id(), Math.log1p(Math.max(0, t.popularity()))));
        Map<Long, Double> popPercentile = Percentiles.of(logPop);
        Map<Long, Double> quality = quality(titles, qualityPriorVotes);

        Map<Long, Entry> entries = new HashMap<>(titles.size() * 2);
        for (TitleFeatures t : titles) {
            Map<Long, CreditRole> roles = new HashMap<>();
            for (TitleFeatures.Credit c : t.credits()) {
                roles.merge(c.personId(), c.role(), (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
            }
            entries.put(t.id(), new Entry(t, vectors.get(t.id()), popPercentile.get(t.id()), quality.get(t.id()),
                    Map.copyOf(roles), Set.copyOf(t.recommendedIds())));
        }
        return new TitleFeatureIndex(Map.copyOf(entries), Map.copyOf(genreNames), Map.copyOf(personNames),
                recommendationBonus);
    }

    /** (v·R + m·C) / (v + m), com C = nota média do catálogo; depois levada para [0, 1] (mín–máx). */
    private static Map<Long, Double> quality(List<TitleFeatures> titles, int m) {
        double c = titles.stream().filter(t -> t.voteAverage() != null && count(t) > 0)
                .mapToDouble(TitleFeatures::voteAverage).average().orElse(0);
        Map<Long, Double> raw = new HashMap<>();
        for (TitleFeatures t : titles) {
            double v = count(t);
            double r = t.voteAverage() == null ? c : t.voteAverage();
            raw.put(t.id(), v + m == 0 ? c : (v * r + m * c) / (v + m));
        }
        double min = raw.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<Long, Double> out = new HashMap<>();
        raw.forEach((id, q) -> out.put(id, max == min ? 0.5 : (q - min) / (max - min)));
        return out;
    }

    private static int count(TitleFeatures t) {
        return t.voteCount() == null ? 0 : Math.max(0, t.voteCount());
    }

    public Optional<Entry> get(long id) {
        return Optional.ofNullable(entries.get(id));
    }

    public boolean contains(long id) {
        return entries.containsKey(id);
    }

    public Collection<Entry> entries() {
        return entries.values();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String genreName(long id) {
        return genreNames.get(id);
    }

    public String personName(long id) {
        return personNames.get(id);
    }

    /** Cosseno dos vetores, mais um bônus se a TMDB recomenda um a partir do outro. */
    public double similarity(long a, long b) {
        Entry x = entries.get(a);
        Entry y = entries.get(b);
        if (x == null || y == null || a == b) return 0;
        return similarity(x, y);
    }

    public double similarity(Entry x, Entry y) {
        double cos = x.vector().cosine(y.vector());
        boolean recommended = x.recommended().contains(y.id()) || y.recommended().contains(x.id());
        return recommended ? cos + recommendationBonus : cos;
    }

    /**
     * Os {@code n} mais parecidos com o título, do mais parecido ao menos. Desempata pela popularidade.
     * {@code skip} tira candidatos (ex.: o que o usuário já curtiu ou viu).
     */
    public List<Long> topSimilar(long id, int n, LongPredicate skip) {
        Entry base = entries.get(id);
        if (base == null || n <= 0) return List.of();
        record Scored(Entry e, double s) {}
        List<Scored> all = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.id() == id || skip.test(e.id())) continue;
            all.add(new Scored(e, similarity(base, e)));
        }
        all.sort(Comparator.comparingDouble(Scored::s).reversed()
                .thenComparing(s -> -s.e().features().popularity())
                .thenComparingLong(s -> s.e().id()));
        return all.stream().limit(n).map(s -> s.e().id()).toList();
    }

    public List<Long> topSimilar(long id, int n) {
        return topSimilar(id, n, x -> false);
    }
}
