package br.com.jotave.wannawatch.feedback;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passa, talvez, já vi e salvar. Cada escrita que muda o estado também vai para o log
 * ({@link InteractionLog}); repetir a mesma escrita não muda nada nem duplica o log.
 */
@Service
@Transactional
public class SignalService {

    private final JdbcClient jdbc;
    private final InteractionLog log;

    public SignalService(JdbcClient jdbc, InteractionLog log) {
        this.jdbc = jdbc;
        this.log = log;
    }

    public record TitleSignals(long titleId, Set<SignalKind> kinds) {}

    /**
     * Cria o sinal. Exceção: marcar "talvez" de novo renova a data, porque o talvez some dos feeds
     * só por alguns dias e quem marca de novo quer adiar outra vez.
     */
    public void put(AuthUser user, long titleId, SignalKind kind, InteractionSource source) {
        requireTitle(titleId);
        String conflict = kind == SignalKind.MAYBE ? "DO UPDATE SET created_at = now()" : "DO NOTHING";
        int changed = jdbc.sql("""
                INSERT INTO user_title_signal (user_id, title_id, kind) VALUES (:u, :t, :k)
                ON CONFLICT (user_id, title_id, kind) %s""".formatted(conflict))
                .param("u", user.id()).param("t", titleId).param("k", kind.name())
                .update();
        if (changed > 0) log.record(user.id(), titleId, InteractionKind.of(kind), source);
    }

    public void remove(AuthUser user, long titleId, SignalKind kind, InteractionSource source) {
        requireTitle(titleId);
        int removed = jdbc.sql("DELETE FROM user_title_signal WHERE user_id = :u AND title_id = :t AND kind = :k")
                .param("u", user.id()).param("t", titleId).param("k", kind.name())
                .update();
        if (removed > 0) log.record(user.id(), titleId, InteractionKind.SIGNAL_REMOVED, kind.name(), source, null);
    }

    /** Uma entrada por id pedido, na mesma ordem, mesmo sem sinais. */
    @Transactional(readOnly = true)
    public List<TitleSignals> forTitles(AuthUser user, List<Long> titleIds) {
        Set<Long> ids = new LinkedHashSet<>(titleIds);
        if (ids.isEmpty()) return List.of();
        Map<Long, Set<SignalKind>> byTitle = new HashMap<>();
        jdbc.sql("SELECT title_id, kind FROM user_title_signal WHERE user_id = :u AND title_id IN (:ids)")
                .param("u", user.id()).param("ids", ids)
                .query((rs, n) -> byTitle.computeIfAbsent(rs.getLong("title_id"), k -> EnumSet.noneOf(SignalKind.class))
                        .add(SignalKind.valueOf(rs.getString("kind"))))
                .list();
        List<TitleSignals> out = new ArrayList<>(ids.size());
        for (long id : ids) out.add(new TitleSignals(id, byTitle.getOrDefault(id, EnumSet.noneOf(SignalKind.class))));
        return out;
    }

    private void requireTitle(long titleId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM title WHERE id = :id)")
                .param("id", titleId).query(Boolean.class).single();
        if (!exists) throw new NotFoundException("Título " + titleId + " não encontrado.");
    }
}
