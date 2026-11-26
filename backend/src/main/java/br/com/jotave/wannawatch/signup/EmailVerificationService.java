package br.com.jotave.wannawatch.signup;

import br.com.jotave.wannawatch.auth.AccountStatus;
import br.com.jotave.wannawatch.auth.AuthService;
import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import br.com.jotave.wannawatch.shared.web.ConflictException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmação de e-mail por código de 6 dígitos. Cada conta tem no máximo um código válido (pedir
 * outro substitui o anterior); ele vale 30 minutos e aceita 5 tentativas erradas.
 */
@Service
@Transactional
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(30);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private final JdbcClient jdbc;
    private final AuthService auth;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(JdbcClient jdbc, AuthService auth, Clock clock,
                                    ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.clock = clock;
        this.events = events;
    }

    /** Gera um código novo para a conta e manda por e-mail (depois do commit). */
    void issue(long userId) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant now = clock.instant();
        jdbc.sql("""
                INSERT INTO email_verification (user_id, code_hash, attempts, sent_at, expires_at)
                VALUES (:u, :h, 0, :now, :exp)
                ON CONFLICT (user_id) DO UPDATE
                    SET code_hash = EXCLUDED.code_hash, attempts = 0,
                        sent_at = EXCLUDED.sent_at, expires_at = EXCLUDED.expires_at""")
                .param("u", userId).param("h", hash(userId, code))
                .param("now", Timestamp.from(now)).param("exp", Timestamp.from(now.plus(CODE_TTL)))
                .update();
        record Who(String email, String name) {}
        Who who = jdbc.sql("SELECT email, display_name FROM app_user WHERE id = :id").param("id", userId)
                .query((rs, n) -> new Who(rs.getString("email"), rs.getString("display_name"))).single();
        events.publishEvent(new VerificationCodeIssued(who.email(), who.name(), code));
    }

    /** Manda outro código (no máximo um por minuto). */
    public void resend(AuthUser user) {
        if (auth.status(user.id()).emailVerified()) throw new ConflictException("Seu e-mail já está confirmado.");
        OffsetDateTime sentAt = jdbc.sql("SELECT sent_at FROM email_verification WHERE user_id = :u FOR UPDATE")
                .param("u", user.id()).query(OffsetDateTime.class).optional().orElse(null);
        if (sentAt != null && sentAt.toInstant().plus(RESEND_COOLDOWN).isAfter(clock.instant()))
            throw new BadRequestException("Acabamos de mandar um código. Espere um minuto para pedir outro.");
        issue(user.id());
    }

    /**
     * Confere o código. Erro não desfaz a transação: a tentativa errada precisa ficar contada.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public AccountStatus verify(AuthUser user, String code) {
        AccountStatus status = auth.status(user.id());
        if (status.emailVerified()) return status;

        record Pending(String hash, int attempts, OffsetDateTime expiresAt) {}
        Pending pending = jdbc.sql("""
                SELECT code_hash, attempts, expires_at FROM email_verification WHERE user_id = :u FOR UPDATE""")
                .param("u", user.id())
                .query((rs, n) -> new Pending(rs.getString("code_hash"), rs.getInt("attempts"),
                        rs.getObject("expires_at", OffsetDateTime.class)))
                .optional().orElse(null);
        if (pending == null || pending.expiresAt().toInstant().isBefore(clock.instant()))
            throw new BadRequestException("Esse código venceu. Peça um novo.");
        if (pending.attempts() >= MAX_ATTEMPTS)
            throw new BadRequestException("Muitas tentativas erradas. Peça um novo código.");

        String typed = code == null ? "" : code.replaceAll("\\s", "");
        if (!pending.hash().equals(hash(user.id(), typed))) {
            jdbc.sql("UPDATE email_verification SET attempts = attempts + 1 WHERE user_id = :u")
                    .param("u", user.id()).update();
            int left = MAX_ATTEMPTS - pending.attempts() - 1;
            throw new BadRequestException(left > 0
                    ? "Código incorreto. Confira o e-mail e tente de novo."
                    : "Muitas tentativas erradas. Peça um novo código.");
        }
        jdbc.sql("UPDATE app_user SET email_verified_at = :now WHERE id = :u")
                .param("now", Timestamp.from(clock.instant())).param("u", user.id()).update();
        jdbc.sql("DELETE FROM email_verification WHERE user_id = :u").param("u", user.id()).update();
        return auth.status(user.id());
    }

    /** O id entra no hash: o mesmo código em duas contas não vira o mesmo hash. */
    private static String hash(long userId, String code) {
        return AuthService.sha256(userId + ":" + code);
    }
}
