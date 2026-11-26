package br.com.jotave.wannawatch.feedback;

/** Tipos de evento do log {@code interaction_event}. */
public enum InteractionKind {
    LIKE, UNLIKE, PASS, MAYBE, SEEN, SAVED, SIGNAL_REMOVED, MATCH_VOTE_YES, MATCH_VOTE_NO, MATCH_UNVOTE;

    static InteractionKind of(SignalKind signal) {
        return valueOf(signal.name());
    }
}
