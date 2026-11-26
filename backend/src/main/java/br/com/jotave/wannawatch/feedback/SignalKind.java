package br.com.jotave.wannawatch.feedback;

import br.com.jotave.wannawatch.shared.web.BadRequestException;
import java.util.Locale;

/** Sinais guardados em {@code user_title_signal}. O "quero ver" fica à parte, em {@code title_like}. */
public enum SignalKind {
    PASS, MAYBE, SEEN, SAVED;

    /** Aceita maiúsculas ou minúsculas; valor desconhecido = 400. */
    public static SignalKind parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new BadRequestException("Sinal desconhecido: " + value + ". Use PASS, MAYBE, SEEN ou SAVED.");
        }
    }
}
