package br.com.jotave.wannawatch.recommendation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gosto de um usuário: dois vetores no mesmo espaço dos títulos (o que ele gosta e o que ele evita)
 * e o histórico que decide o que sai dos feeds.
 *
 * @param positive        Σ peso·decaimento·vetor das interações positivas (+ gênero favorito)
 * @param negative        o mesmo para as negativas, com o peso em módulo
 * @param positiveSignals quantos títulos têm sinal positivo (para o cold start)
 * @param anchors         curtidos e salvos, do mais recente ao mais antigo ("Porque você curtiu X")
 * @param maybeAt         quando cada "talvez" foi marcado
 */
public record TasteProfile(
        SparseVector positive,
        SparseVector negative,
        int positiveSignals,
        Long favoriteGenreId,
        List<Long> anchors,
        Set<Long> liked,
        Set<Long> saved,
        Set<Long> seen,
        Set<Long> passed,
        Map<Long, Instant> maybeAt) {

    public static final TasteProfile EMPTY = new TasteProfile(SparseVector.EMPTY, SparseVector.EMPTY, 0, null,
            List.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());

    /** Interações de títulos fora do índice são ignoradas. */
    public static TasteProfile build(List<Interaction> interactions, Long favoriteGenreId, TitleFeatureIndex index,
                                     SignalWeights weights, Instant now) {
        SparseVector.Builder positive = SparseVector.builder();
        SparseVector.Builder negative = SparseVector.builder();
        Set<Long> positiveTitles = new HashSet<>();
        Set<Long> liked = new HashSet<>();
        Set<Long> saved = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> passed = new HashSet<>();
        Map<Long, Instant> maybeAt = new HashMap<>();
        Map<Long, Instant> anchorAt = new HashMap<>();

        for (Interaction i : interactions) {
            var entry = index.get(i.titleId());
            if (entry.isEmpty()) continue;
            switch (i.type()) {
                case LIKE -> liked.add(i.titleId());
                case SAVED -> saved.add(i.titleId());
                case SEEN -> seen.add(i.titleId());
                case PASS -> passed.add(i.titleId());
                case MAYBE -> maybeAt.merge(i.titleId(), i.at(), (a, b) -> a.isAfter(b) ? a : b);
                default -> { }
            }
            if (i.type() == InteractionType.LIKE || i.type() == InteractionType.SAVED) {
                anchorAt.merge(i.titleId(), i.at(), (a, b) -> a.isAfter(b) ? a : b);
            }
            double w = weights.of(i.type());
            if (w == 0) continue;
            double decay = i.type() == InteractionType.FAVORITE_TITLE ? 1 : decay(i.at(), now, weights.halfLifeDays());
            if (w > 0) {
                positive.add(entry.get().vector(), w * decay);
                positiveTitles.add(i.titleId());
            } else {
                negative.add(entry.get().vector(), -w * decay);
            }
        }
        if (favoriteGenreId != null && weights.favoriteGenre() != 0) {
            positive.add(FeatureVectorizer.genreKey(favoriteGenreId), weights.favoriteGenre());
        }

        List<Long> anchors = new ArrayList<>(anchorAt.keySet());
        anchors.sort(Comparator.comparing((Long id) -> anchorAt.get(id)).reversed().thenComparing(id -> id));
        return new TasteProfile(positive.build(), negative.build(), positiveTitles.size(), favoriteGenreId,
                List.copyOf(new LinkedHashSet<>(anchors)), Set.copyOf(liked), Set.copyOf(saved), Set.copyOf(seen),
                Set.copyOf(passed), Map.copyOf(maybeAt));
    }

    /** 0.5^(idade em dias / meia-vida). Datas no futuro contam como agora. */
    static double decay(Instant at, Instant now, double halfLifeDays) {
        if (at == null || halfLifeDays <= 0) return 1;
        double days = Math.max(0, Duration.between(at, now).toMillis() / 86_400_000.0);
        return Math.pow(0.5, days / halfLifeDays);
    }

    /** Peso do gênero no perfil positivo (para escolher a fileira de gênero e a exploração). */
    public double genreWeight(long genreId) {
        return positive.get(FeatureVectorizer.genreKey(genreId));
    }

    public boolean excluded(long titleId, Set<Exclusion> rules, Instant now, Duration maybeCooldown) {
        for (Exclusion r : rules) {
            boolean hit = switch (r) {
                case LIKED -> liked.contains(titleId);
                case SAVED -> saved.contains(titleId);
                case SEEN -> seen.contains(titleId);
                case PASSED -> passed.contains(titleId);
                case RECENT_MAYBE -> {
                    Instant at = maybeAt.get(titleId);
                    yield at != null && at.plus(maybeCooldown).isAfter(now);
                }
            };
            if (hit) return true;
        }
        return false;
    }
}
