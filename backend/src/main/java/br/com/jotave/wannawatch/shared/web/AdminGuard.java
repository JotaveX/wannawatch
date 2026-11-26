package br.com.jotave.wannawatch.shared.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Confere o header X-Admin-Token (valor do ADMIN_TOKEN). Sem ADMIN_TOKEN, os endpoints de admin
 * respondem 404. Token errado é 403, e não 401, para o front não confundir com sessão vencida.
 */
@Component
public class AdminGuard {

    private final String expected;

    public AdminGuard(@Value("${wannawatch.import.admin-token:}") String expected) {
        this.expected = expected;
    }

    public void check(String token) {
        if (expected == null || expected.isBlank()) {
            throw new NotFoundException("Endpoint de admin desligado (ADMIN_TOKEN vazio).");
        }
        boolean ok = token != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new ForbiddenException("X-Admin-Token inválido ou ausente.");
    }
}
