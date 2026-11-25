package br.com.jotave.wannawatch.profile;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.profile.ProfileService.Activity;
import br.com.jotave.wannawatch.profile.ProfileService.Changes;
import br.com.jotave.wannawatch.profile.ProfileService.Profile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil de quem está logado. E-mail e arroba são só leitura por enquanto. */
@RestController
@RequestMapping("/api/me/profile")
@Tag(name = "Perfil")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 40) String displayName,
            @Min(0) @Max(ProfileService.COLORS - 1) int color,
            @Min(0) @Max(ProfileService.MASCOTS - 1) Integer mascot,
            Long favoriteGenreId,
            Long favoriteTitleId) {}

    @GetMapping
    @Operation(summary = "Meu perfil, com gênero e título favoritos e meus números")
    public Profile get(@Parameter(hidden = true) AuthUser user) {
        return profiles.get(user);
    }

    @GetMapping("/activity")
    @Operation(summary = "Curtidas por semana (últimas 12) e os últimos curtidos, para a página de perfil")
    public Activity activity(@Parameter(hidden = true) AuthUser user) {
        return profiles.activity(user);
    }

    @PutMapping
    @Operation(summary = "Salva nome, cor, Bu e favoritos (substitui tudo; null limpa o campo)")
    public Profile update(@Parameter(hidden = true) AuthUser user, @Valid @RequestBody UpdateRequest body) {
        return profiles.update(user, new Changes(body.displayName(), body.color(), body.mascot(),
                body.favoriteGenreId(), body.favoriteTitleId()));
    }
}
