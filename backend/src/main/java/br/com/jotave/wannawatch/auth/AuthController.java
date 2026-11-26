package br.com.jotave.wannawatch.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login, sessão e status do cadastro. O cadastro em si fica em {@code signup}. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Login")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** @param username usuário ou e-mail */
    public record LoginRequest(@NotBlank @Size(max = 254) String username, @NotBlank @Size(max = 200) String password) {}

    /** @param account em que etapa do cadastro a conta está (o front leva para a que falta) */
    public record LoginResponse(String token, UserRef user, AccountStatus account) {}

    public record AccountResponse(UserRef user, AccountStatus account) {}

    @PostMapping("/login")
    @Operation(summary = "Entra com usuário (ou e-mail) e senha e devolve o token (Authorization: Bearer)")
    public LoginResponse login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body.username(), body.password())
                .map(l -> new LoginResponse(l.token(), l.user().ref(), auth.status(l.user().id())))
                .orElseThrow(() -> new UnauthorizedException("Usuário ou senha incorretos."));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra a sessão do token enviado")
    public void logout(HttpServletRequest request) {
        auth.logout(AuthUserResolver.bearerToken(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Quem está logado")
    public UserRef me(@Parameter(hidden = true) AuthUser user) {
        return user.ref();
    }

    @GetMapping("/account")
    @Operation(summary = "Quem está logado e em que etapa do cadastro está")
    public AccountResponse account(@Parameter(hidden = true) AuthUser user) {
        return new AccountResponse(user.ref(), auth.status(user.id()));
    }
}
