package br.com.jotave.wannawatch.social;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.social.LikeService.FriendsLiked;
import br.com.jotave.wannawatch.social.LikeService.TitleLikes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tudo aqui exige login. */
@RestController
@RequestMapping("/api/likes")
@Tag(name = "Curtidas")
public class LikeController {

    private final LikeService likes;

    public LikeController(LikeService likes) {
        this.likes = likes;
    }

    @PutMapping("/{titleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca \"quero ver\" (idempotente)")
    public void like(@Parameter(hidden = true) AuthUser user, @PathVariable long titleId) {
        likes.like(user, titleId);
    }

    @DeleteMapping("/{titleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desfaz o \"quero ver\" (idempotente)")
    public void unlike(@Parameter(hidden = true) AuthUser user, @PathVariable long titleId) {
        likes.unlike(user, titleId);
    }

    @GetMapping
    @Operation(summary = "Para cada título: se eu curti e quais amigos curtiram")
    public List<TitleLikes> forTitles(@Parameter(hidden = true) AuthUser user,
                                      @RequestParam @Size(min = 1, max = 100) List<Long> titleIds) {
        return likes.forTitles(user, titleIds);
    }

    @GetMapping("/friends")
    @Operation(summary = "Títulos que os amigos curtiram (fileira da Home)")
    public List<FriendsLiked> friendsLiked(@Parameter(hidden = true) AuthUser user,
                                           @RequestParam(defaultValue = "15") @Min(1) @Max(50) int size) {
        return likes.friendsLiked(user, size);
    }
}
