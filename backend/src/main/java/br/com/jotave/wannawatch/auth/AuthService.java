package br.com.jotave.wannawatch.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login por usuário (ou e-mail) e senha, com token opaco. O token (aleatório, 256 bits) vai para o cliente;
 * no banco fica só o SHA-256 dele. A senha é conferida pelo próprio Postgres (bcrypt do pgcrypto).
 */
@Service
@Transactional
public class AuthService {

    static final Duration SESSION_TTL = Duration.ofDays(30);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Login(String token, AuthUser user) {}

    public Optional<Login> login(String username, String password) {
        Optional<AuthUser> user = jdbc.sql("""
                SELECT %s FROM app_user u
                WHERE (u.username = lower(trim(:u)) OR lower(u.email) = lower(trim(:u)))
                  AND u.password_hash = crypt(:p, u.password_hash)""".formatted(UserRef.COLUMNS))
                .param("u", username).param("p", password)
                .query((rs, n) -> AuthUser.of(UserRef.from(rs)))
                .optional();
        return user.map(u -> new Login(startSession(u.id()), u));
    }

    /** Em que etapa do cadastro a conta está. */
    @Transactional(readOnly = true)
    public AccountStatus status(long userId) {
        return jdbc.sql("SELECT email, email_verified_at, titles_picked_at, is_admin FROM app_user WHERE id = :id")
                .param("id", userId)
                .query((rs, n) -> new AccountStatus(rs.getString("email"),
                        rs.getObject("email_verified_at") != null, rs.getObject("titles_picked_at") != null,
                        rs.getBoolean("is_admin")))
                .single();
    }

    @Transactional(readOnly = true)
    public UserRef ref(long userId) {
        return jdbc.sql("SELECT %s FROM app_user u WHERE u.id = :id".formatted(UserRef.COLUMNS))
                .param("id", userId).query((rs, n) -> UserRef.from(rs)).single();
    }

    /** Cria uma sessão nova e devolve o token (só ele; no banco fica o hash). */
    public String startSession(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("INSERT INTO auth_session (token_hash, user_id, expires_at) VALUES (:h, :u, :exp)")
                .param("h", sha256(token)).param("u", userId)
                .param("exp", Timestamp.from(clock.instant().plus(SESSION_TTL)))
                .update();
        // aproveita para limpar sessões vencidas
        jdbc.sql("DELETE FROM auth_session WHERE expires_at < :now")
                .param("now", Timestamp.from(clock.instant())).update();
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<AuthUser> userByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return jdbc.sql("""
                SELECT %s
                FROM auth_session s JOIN app_user u ON u.id = s.user_id
                WHERE s.token_hash = :h AND s.expires_at > :now""".formatted(UserRef.COLUMNS))
                .param("h", sha256(token)).param("now", Timestamp.from(clock.instant()))
                .query((rs, n) -> AuthUser.of(UserRef.from(rs)))
                .optional();
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        jdbc.sql("DELETE FROM auth_session WHERE token_hash = :h").param("h", sha256(token)).update();
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
