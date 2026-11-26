package br.com.jotave.wannawatch.auth;

import br.com.jotave.wannawatch.shared.web.ForbiddenException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Endpoints só de administrador: pedem login (AuthUser) e {@code app_user.is_admin}; senão 403. */
@Component
public class AdminGuard {

    private final JdbcClient jdbc;

    public AdminGuard(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void require(AuthUser user) {
        boolean admin = jdbc.sql("SELECT is_admin FROM app_user WHERE id = :id")
                .param("id", user.id()).query(Boolean.class).optional().orElse(false);
        if (!admin) throw new ForbiddenException("Só administradores podem fazer isso.");
    }
}
