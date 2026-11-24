package br.com.jotave.wannawatch.shared.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cursor opaco: base64url de "v1|chave|id". Para o cliente é só uma string que ele devolve.
 * A chave é o valor da ordenação do último item (popularidade, hash...) e o id desempata.
 */
public record Cursor(String key, long id) {

    private static final String VERSION = "v1";

    public String encode() {
        String raw = VERSION + "|" + key + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Nulo ou vazio = primeira página. Cursor malformado = 400. */
    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) throw new IllegalArgumentException();
            return new Cursor(parts[1], Long.parseLong(parts[2]));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Cursor inválido.");
        }
    }

    public double keyAsDouble() {
        try {
            return Double.parseDouble(key);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Cursor inválido.");
        }
    }
}
