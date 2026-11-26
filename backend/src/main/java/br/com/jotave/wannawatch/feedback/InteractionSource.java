package br.com.jotave.wannawatch.feedback;

import br.com.jotave.wannawatch.shared.web.BadRequestException;
import java.util.Locale;

/** De que tela veio a interação. Opcional: sem ele o evento fica com {@code source} nulo. */
public enum InteractionSource {
    SWIPE, REELS, DETAILS, HOME, MATCH,
    /** títulos escolhidos no cadastro */
    ONBOARDING;

    /** Nulo ou vazio = sem origem; valor desconhecido = 400. */
    public static InteractionSource parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new BadRequestException("Origem desconhecida: " + value + ".");
        }
    }
}
