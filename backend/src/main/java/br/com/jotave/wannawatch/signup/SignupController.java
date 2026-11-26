package br.com.jotave.wannawatch.signup;

import br.com.jotave.wannawatch.auth.AccountStatus;
import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.signup.SignupService.NewAccount;
import br.com.jotave.wannawatch.signup.SignupService.Registered;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro em 3 etapas: conta → títulos de que gosta → confirmar o e-mail. A conta já entra logada
 * na primeira; o front leva para a etapa que falta usando o {@code account} das respostas.
 * As regras de cada campo ficam no {@link SignupService} (mensagens em português).
 */
@RestController
@Tag(name = "Cadastro")
public class SignupController {

    private final SignupService signup;
    private final EmailVerificationService verification;

    public SignupController(SignupService signup, EmailVerificationService verification) {
        this.signup = signup;
        this.verification = verification;
    }

    public record RegisterRequest(String displayName, String username, String email, String password) {}

    public record VerifyRequest(@NotBlank @Size(max = 20) String code) {}

    public record PickTitlesRequest(@NotNull @Size(max = SignupService.MAX_TITLES) List<Long> titleIds,
                                    Long favoriteTitleId) {}

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria a conta (já logada) e manda o código de confirmação por e-mail")
    public Registered register(@RequestBody RegisterRequest body) {
        return signup.register(new NewAccount(body.displayName(), body.username(), body.email(), body.password()));
    }

    @PostMapping("/api/auth/email/verify")
    @Operation(summary = "Confirma o e-mail com o código de 6 dígitos")
    public AccountStatus verify(@Parameter(hidden = true) AuthUser user, @Valid @RequestBody VerifyRequest body) {
        return verification.verify(user, body.code());
    }

    @PostMapping("/api/auth/email/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Manda outro código (no máximo um por minuto)")
    public void resend(@Parameter(hidden = true) AuthUser user) {
        verification.resend(user);
    }

    @PutMapping("/api/me/onboarding/titles")
    @Operation(summary = "Etapa 2: os títulos de que gosta (5 a 50, viram \"quero ver\") e o favorito, que precisa estar entre eles")
    public AccountStatus pickTitles(@Parameter(hidden = true) AuthUser user, @Valid @RequestBody PickTitlesRequest body) {
        return signup.pickTitles(user, body.titleIds(), body.favoriteTitleId());
    }
}
