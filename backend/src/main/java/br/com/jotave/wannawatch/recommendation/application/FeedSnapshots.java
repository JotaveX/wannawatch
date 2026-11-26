package br.com.jotave.wannawatch.recommendation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Ordem de um feed personalizado guardada na primeira página, para a paginação não pular nem
 * repetir títulos enquanto o score muda com os swipes. Fica em memória: se expirar (ou o back
 * reiniciar), o feed é recalculado do começo e as exclusões evitam repetir o que já foi avaliado.
 */
@Component
public class FeedSnapshots {

    private final Clock clock;
    private final RecommendationProperties props;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    private record Snapshot(List<Long> ids, Instant expiresAt) {}

    public FeedSnapshots(Clock clock, RecommendationProperties props) {
        this.clock = clock;
        this.props = props;
    }

    public Optional<List<Long>> get(String key) {
        Snapshot s = snapshots.get(key);
        if (s == null || !s.expiresAt().isAfter(clock.instant())) return Optional.empty();
        return Optional.of(s.ids());
    }

    public List<Long> put(String key, List<Long> ids) {
        Instant now = clock.instant();
        snapshots.values().removeIf(s -> !s.expiresAt().isAfter(now));
        List<Long> copy = List.copyOf(ids);
        snapshots.put(key, new Snapshot(copy, now.plus(props.feed().snapshotTtl())));
        return copy;
    }

    /** Chave do snapshot: um por usuário, feed, seed e filtros. */
    public static String key(long userId, String feed, String seed, Object type, String genre) {
        return userId + "|" + feed + "|" + seed + "|" + type + "|" + (genre == null ? "" : genre.trim().toLowerCase());
    }
}
