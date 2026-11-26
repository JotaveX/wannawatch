package br.com.jotave.wannawatch.recommendation.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Percentil de cada valor dentro do conjunto: 0 = o menor, 1 = o maior. Empates ficam com o posto
 * médio (seis títulos empatados no topo não podem ganhar o percentil do sexto colocado).
 */
public final class Percentiles {

    private Percentiles() {}

    public static Map<Long, Double> of(Map<Long, Double> values) {
        int n = values.size();
        Map<Long, Double> out = new HashMap<>(n * 2);
        if (n == 0) return out;
        if (n == 1) {
            values.keySet().forEach(k -> out.put(k, 1.0));
            return out;
        }
        double[] sorted = values.values().stream().mapToDouble(Double::doubleValue).sorted().toArray();
        values.forEach((k, v) -> {
            int less = lowerBound(sorted, v);
            int equal = upperBound(sorted, v) - less;
            out.put(k, (less + (equal - 1) / 2.0) / (n - 1));
        });
        return out;
    }

    /** Quantos valores são menores ou iguais a {@code v}. */
    private static int upperBound(double[] sorted, double v) {
        int i = lowerBound(sorted, v);
        while (i < sorted.length && sorted[i] == v) i++;
        return i;
    }

    /** Quantos valores são estritamente menores que {@code v}. */
    private static int lowerBound(double[] sorted, double v) {
        int i = Arrays.binarySearch(sorted, v);
        if (i < 0) return -i - 1;
        while (i > 0 && sorted[i - 1] == v) i--;
        return i;
    }
}
