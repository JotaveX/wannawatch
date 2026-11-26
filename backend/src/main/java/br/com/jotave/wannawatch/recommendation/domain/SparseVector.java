package br.com.jotave.wannawatch.recommendation.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Vetor esparso imutável (chave da feature → peso). Zeros não são guardados. */
public final class SparseVector {

    public static final SparseVector EMPTY = new SparseVector(Map.of());

    private final Map<String, Double> values;
    private final double norm;

    private SparseVector(Map<String, Double> values) {
        this.values = values;
        double sum = 0;
        for (double v : values.values()) sum += v * v;
        this.norm = Math.sqrt(sum);
    }

    public static SparseVector of(Map<String, Double> values) {
        Map<String, Double> copy = new HashMap<>();
        values.forEach((k, v) -> {
            if (v != null && v != 0 && Double.isFinite(v)) copy.put(k, v);
        });
        return copy.isEmpty() ? EMPTY : new SparseVector(Collections.unmodifiableMap(copy));
    }

    public double get(String key) {
        return values.getOrDefault(key, 0.0);
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public Map<String, Double> asMap() {
        return values;
    }

    public double norm() {
        return norm;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Norma L2 = 1 (vetor vazio continua vazio). */
    public SparseVector normalized() {
        return norm == 0 || norm == 1 ? this : scaled(1 / norm);
    }

    public SparseVector scaled(double factor) {
        if (factor == 0) return EMPTY;
        Map<String, Double> out = new HashMap<>(values.size());
        values.forEach((k, v) -> out.put(k, v * factor));
        return new SparseVector(Collections.unmodifiableMap(out));
    }

    public double dot(SparseVector other) {
        SparseVector small = values.size() <= other.values.size() ? this : other;
        SparseVector big = small == this ? other : this;
        double sum = 0;
        for (Map.Entry<String, Double> e : small.values.entrySet()) {
            Double v = big.values.get(e.getKey());
            if (v != null) sum += e.getValue() * v;
        }
        return sum;
    }

    /** Cosseno; 0 se algum dos dois for vazio. */
    public double cosine(SparseVector other) {
        if (norm == 0 || other.norm == 0) return 0;
        return dot(other) / (norm * other.norm);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    /** Acumula somas (não é thread-safe; use e descarte). */
    public static final class Builder {

        private final Map<String, Double> values = new HashMap<>();

        public Builder add(String key, double value) {
            values.merge(key, value, Double::sum);
            return this;
        }

        public Builder add(SparseVector vector, double factor) {
            if (factor != 0) vector.values.forEach((k, v) -> add(k, v * factor));
            return this;
        }

        public SparseVector build() {
            return of(values);
        }
    }
}
