package br.com.jotave.wannawatch.signup;

import br.com.jotave.wannawatch.auth.AccountStatus;
import br.com.jotave.wannawatch.auth.AuthService;
import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.auth.UserRef;
import br.com.jotave.wannawatch.feedback.InteractionSource;
import br.com.jotave.wannawatch.feedback.SignalChangedEvent;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import br.com.jotave.wannawatch.shared.web.ConflictException;
import br.com.jotave.wannawatch.social.LikeService;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro em etapas: (1) criar a conta, que já entra logada e recebe o código por e-mail;
 * (2) escolher os títulos de que gosta, que viram "quero ver" e alimentam a recomendação;
 * (3) confirmar o e-mail ({@link EmailVerificationService}).
 */
@Service
@Transactional
public class SignupService {

    public static final int MIN_TITLES = 5;
    public static final int MAX_TITLES = 50;
    /** tamanho da paleta de cores do front (frontend/src/app/core/social/user-colors.ts) */
    private static final int COLORS = 8;

    static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9_.]{2,19}");
    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final JdbcClient jdbc;
    private final AuthService auth;
    private final EmailVerificationService verification;
    private final LikeService likes;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SignupService(JdbcClient jdbc, AuthService auth, EmailVerificationService verification,
                         LikeService likes, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.verification = verification;
        this.likes = likes;
        this.events = events;
        this.clock = clock;
    }

    public record NewAccount(String displayName, String username, String email, String password) {}

    public record Registered(String token, UserRef user, AccountStatus account) {}

    public Registered register(NewAccount account) {
        String name = account.displayName() == null ? "" : account.displayName().strip().replaceAll("\\s+", " ");
        String username = account.username() == null ? "" : account.username().strip().toLowerCase(Locale.ROOT);
        String email = account.email() == null ? "" : account.email().strip().toLowerCase(Locale.ROOT);
        String password = account.password() == null ? "" : account.password();

        if (name.isEmpty()) throw new BadRequestException("Diga como quer ser chamado.");
        if (name.length() > 40) throw new BadRequestException("O nome pode ter até 40 letras.");
        if (!USERNAME.matcher(username).matches())
            throw new BadRequestException("O usuário precisa de 3 a 20 caracteres: letras minúsculas, números, _ ou ponto.");
        if (email.length() > 254 || !EMAIL.matcher(email).matches())
            throw new BadRequestException("Esse e-mail não parece certo.");
        if (password.length() < 8) throw new BadRequestException("A senha precisa de pelo menos 8 caracteres.");
        if (password.length() > 200) throw new BadRequestException("A senha pode ter até 200 caracteres.");

        if (taken("username = :v", username)) throw new ConflictException("Esse usuário já existe. Tente outro.");
        if (taken("lower(email) = :v", email)) throw new ConflictException("Já existe uma conta com esse e-mail.");

        long id;
        try {
            id = jdbc.sql("""
                    INSERT INTO app_user (username, display_name, email, password_hash, color)
                    VALUES (:u, :name, :email, crypt(:p, gen_salt('bf', 10)), :color)
                    RETURNING id""")
                    .param("u", username).param("name", name).param("email", email).param("p", password)
                    .param("color", random.nextInt(COLORS))
                    .query(Long.class).single();
        } catch (DuplicateKeyException e) {
            // duas pessoas pedindo o mesmo usuário/e-mail ao mesmo tempo
            throw new ConflictException("Esse usuário ou e-mail acabou de ser usado. Tente outro.");
        }
        verification.issue(id);
        return new Registered(auth.startSession(id), auth.ref(id), auth.status(id));
    }

    /**
     * Etapa 2: os títulos escolhidos viram "quero ver" e o favorito vai para o perfil.
     * Pode repetir (ex.: voltou a etapa): curtir de novo não duplica nada.
     */
    public AccountStatus pickTitles(AuthUser user, List<Long> titleIds, Long favoriteTitleId) {
        Set<Long> ids = new LinkedHashSet<>(titleIds == null ? List.of() : titleIds);
        ids.remove(null);
        if (ids.size() < MIN_TITLES) throw new BadRequestException("Escolha pelo menos " + MIN_TITLES + " títulos.");
        if (ids.size() > MAX_TITLES) throw new BadRequestException("Escolha no máximo " + MAX_TITLES + " títulos.");
        if (favoriteTitleId != null && !ids.contains(favoriteTitleId))
            throw new BadRequestException("O favorito precisa estar entre os escolhidos.");
        long found = jdbc.sql("SELECT count(*) FROM title WHERE id IN (:ids)").param("ids", ids)
                .query(Long.class).single();
        if (found != ids.size()) throw new BadRequestException("Algum dos títulos escolhidos não existe mais.");

        for (long id : ids) likes.like(user, id, InteractionSource.ONBOARDING);
        jdbc.sql("""
                UPDATE app_user
                SET favorite_title_id = coalesce(:fav, favorite_title_id),
                    titles_picked_at = coalesce(titles_picked_at, :now)
                WHERE id = :id""")
                .param("fav", favoriteTitleId).param("now", Timestamp.from(clock.instant())).param("id", user.id())
                .update();
        // o favorito também entra no perfil de gosto
        events.publishEvent(new SignalChangedEvent(user.id()));
        return auth.status(user.id());
    }

    private boolean taken(String condition, String value) {
        // condition vem só das duas constantes acima, nunca da requisição
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM app_user WHERE " + condition + ")")
                .param("v", value).query(Boolean.class).single();
    }
}
