package br.com.jotave.wannawatch.feedback;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava no log append-only {@code interaction_event}, sempre na transação de quem chama, e avisa
 * que o gosto do usuário mudou ({@link SignalChangedEvent}).
 */
@Component
public class InteractionLog {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    public InteractionLog(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long userId, long titleId, InteractionKind kind, String value,
                       InteractionSource source, Long roomId) {
        // external_id e media_type vêm do próprio título, para o histórico sobreviver a uma reimportação
        jdbc.sql("""
                INSERT INTO interaction_event (user_id, title_external_id, media_type, title_id, kind, value,
                                               source, room_id)
                SELECT :u, t.external_id, t.media_type, t.id, :kind, :value, :source, :room
                FROM title t WHERE t.id = :t""")
                .param("u", userId).param("t", titleId).param("kind", kind.name()).param("value", value)
                .param("source", source == null ? null : source.name()).param("room", roomId)
                .update();
        events.publishEvent(new SignalChangedEvent(userId));
    }

    public void record(long userId, long titleId, InteractionKind kind, InteractionSource source) {
        record(userId, titleId, kind, null, source, null);
    }
}
