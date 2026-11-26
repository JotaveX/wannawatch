package br.com.jotave.wannawatch.recommendation.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transforma títulos em vetores esparsos de conteúdo. Cada família (gênero, keyword, direção,
 * roteiro, elenco, outros da equipe, idioma, década, tipo) é normalizada separadamente, multiplicada
 * pelo peso dela e somada; o vetor final é normalizado (L2).
 *
 * <p>Keyword e elenco são ponderados por IDF ({@code log(N / df)}), para "Nova York" ou um ator
 * onipresente não ligar meio catálogo. Pessoas e keywords que aparecem em menos de
 * {@code minDocumentFrequency} títulos são descartadas: não ligam títulos entre si e só inflam a norma.
 *
 * <p>Chaves: {@code g:<id>}, {@code k:<id>}, {@code p:<id>}, {@code lang:<código>},
 * {@code dec:<ano inicial>}, {@code type:<MOVIE|SERIES>}.
 */
public final class FeatureVectorizer {

    private final FeatureWeights w;

    public FeatureVectorizer(FeatureWeights weights) {
        this.w = weights;
    }

    public static String genreKey(long id) {
        return "g:" + id;
    }

    public static String personKey(long id) {
        return "p:" + id;
    }

    public Map<Long, SparseVector> vectorize(List<TitleFeatures> titles) {
        int n = titles.size();
        Map<String, Integer> df = documentFrequency(titles);
        Map<Long, SparseVector> out = new HashMap<>(n * 2);
        for (TitleFeatures t : titles) out.put(t.id(), vector(t, df, n));
        return out;
    }

    private SparseVector vector(TitleFeatures t, Map<String, Integer> df, int n) {
        SparseVector.Builder genres = SparseVector.builder();
        for (int i = 0; i < t.genreIds().size(); i++) {
            genres.add(genreKey(t.genreIds().get(i)), 1 / (1 + w.genrePositionDecay() * i));
        }

        SparseVector.Builder keywords = SparseVector.builder();
        for (int k : new HashSet<>(t.keywordIds())) {
            String key = "k:" + k;
            if (kept(key, df)) keywords.add(key, idf(key, df, n));
        }

        Map<CreditRole, SparseVector.Builder> people = new EnumMap<>(CreditRole.class);
        for (CreditRole r : CreditRole.values()) people.put(r, SparseVector.builder());
        Set<String> seen = new HashSet<>();
        for (TitleFeatures.Credit c : t.credits()) {
            String key = personKey(c.personId());
            // a mesma pessoa no mesmo papel (ex.: Writer e Screenplay) conta uma vez só
            if (!kept(key, df) || !seen.add(c.role() + key)) continue;
            double value = switch (c.role()) {
                case CAST -> (c.order() < w.castTopBilling() ? w.castTopWeight() : w.castRestWeight())
                        * idf(key, df, n);
                default -> 1.0;
            };
            people.get(c.role()).add(key, value);
        }

        SparseVector.Builder total = SparseVector.builder()
                .add(genres.build().normalized(), w.genre())
                .add(keywords.build().normalized(), w.keyword())
                .add(people.get(CreditRole.DIRECTION).build().normalized(), w.direction())
                .add(people.get(CreditRole.WRITING).build().normalized(), w.writing())
                .add(people.get(CreditRole.CAST).build().normalized(), w.cast())
                .add(people.get(CreditRole.OTHER_CREW).build().normalized(), w.otherCrew())
                .add("type:" + t.mediaType().name(), w.type());
        if (t.originalLanguage() != null) total.add("lang:" + t.originalLanguage(), w.language());
        if (t.year() != null) total.add("dec:" + (t.year() / 10 * 10), w.decade());
        return total.build().normalized();
    }

    /** Em quantos títulos aparece cada pessoa e cada keyword. */
    private static Map<String, Integer> documentFrequency(List<TitleFeatures> titles) {
        Map<String, Integer> df = new HashMap<>();
        for (TitleFeatures t : titles) {
            Set<String> keys = new HashSet<>();
            t.keywordIds().forEach(k -> keys.add("k:" + k));
            t.credits().forEach(c -> keys.add(personKey(c.personId())));
            keys.forEach(k -> df.merge(k, 1, Integer::sum));
        }
        return df;
    }

    private boolean kept(String key, Map<String, Integer> df) {
        return df.getOrDefault(key, 0) >= w.minDocumentFrequency();
    }

    private static double idf(String key, Map<String, Integer> df, int n) {
        return Math.log((double) n / df.get(key));
    }
}
