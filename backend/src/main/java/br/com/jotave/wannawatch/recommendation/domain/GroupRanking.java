package br.com.jotave.wannawatch.recommendation.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ordem do deck de uma sala de Match. Para cada título, com {@code p_i} = percentil do score
 * individual de cada participante (assim quem dá notas altas para tudo não pesa mais):
 *
 * <pre>
 * grupo = média(p_i) − λ·desvio(p_i) + bônus·(quantos querem ver) − penalidade·(quantos já viram)
 *       + ruído·md5(seed:id)
 * </pre>
 *
 * Se alguém tem percentil abaixo do limiar de miséria, o título vai para o fim (alguém
 * provavelmente vai odiar). Se todo mundo já viu, ele fica de fora.
 */
public final class GroupRanking {

    private GroupRanking() {}

    public record Ranked(long titleId, double score) {}

    public static List<Ranked> rank(List<Long> candidates, List<UserScores> participants, GroupParams params,
                                    String seed) {
        record Row(long id, double score, boolean misery) {}
        List<Row> rows = new ArrayList<>();
        int n = participants.size();
        for (long id : candidates) {
            if (n == 0) {
                rows.add(new Row(id, params.noise() * SeededNoise.of(seed, id), false));
                continue;
            }
            double sum = 0;
            double min = 1;
            int liked = 0;
            int seen = 0;
            double[] p = new double[n];
            for (int i = 0; i < n; i++) {
                UserScores u = participants.get(i);
                p[i] = u.percentile(id);
                sum += p[i];
                min = Math.min(min, p[i]);
                if (u.profile().liked().contains(id)) liked++;
                if (u.profile().seen().contains(id)) seen++;
            }
            if (seen == n) continue;
            double mean = sum / n;
            double variance = 0;
            for (double v : p) variance += (v - mean) * (v - mean);
            double sd = Math.sqrt(variance / n);
            double score = mean - params.lambda() * sd + params.likeBonus() * liked - params.seenPenalty() * seen
                    + params.noise() * SeededNoise.of(seed, id);
            rows.add(new Row(id, score, min < params.miseryThreshold()));
        }
        rows.sort(Comparator.comparing(Row::misery).thenComparing(Comparator.comparingDouble(Row::score).reversed())
                .thenComparingLong(Row::id));
        return rows.stream().map(r -> new Ranked(r.id(), r.score())).toList();
    }
}
