package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.auth.AdminGuard;
import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.auth.UnauthorizedException;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import br.com.jotave.wannawatch.tmdb.importer.ImportProperties;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverRequest;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverResult;
import br.com.jotave.wannawatch.video.VideoDtos.StatusRequest;
import br.com.jotave.wannawatch.video.VideoDtos.TitleVideos;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teste dos vídeos do Reels: busca no YouTube e revisão. Só para administradores (login), menos
 * {@code /discover/auto}, que é o job diário do GitHub Actions e usa o header X-Admin-Token.
 */
@RestController
@RequestMapping("/api/admin/videos")
public class VideoAdminController {

    private final AdminGuard guard;
    private final VideoDiscoveryService service;
    private final ImportProperties importProps;

    public VideoAdminController(AdminGuard guard, VideoDiscoveryService service, ImportProperties importProps) {
        this.guard = guard;
        this.service = service;
        this.importProps = importProps;
    }

    @PostMapping("/discover")
    public DiscoverResult discover(AuthUser user, @RequestBody(required = false) DiscoverRequest body) {
        guard.require(user);
        return service.discover(body == null ? DiscoverRequest.defaults() : body);
    }

    /** Mesma busca, sem login: protegida pelo ADMIN_TOKEN (vazio = desligada, 404). */
    @PostMapping("/discover/auto")
    public DiscoverResult discoverAuto(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                       @RequestBody(required = false) DiscoverRequest body) {
        String expected = importProps.adminToken();
        if (expected == null || expected.isBlank()) {
            throw new NotFoundException("Endpoint de admin desligado (ADMIN_TOKEN vazio).");
        }
        if (token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("X-Admin-Token inválido ou ausente.");
        }
        return service.discover(body == null ? DiscoverRequest.defaults() : body);
    }

    @GetMapping
    public List<TitleVideos> list(AuthUser user) {
        guard.require(user);
        return service.list();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> setStatus(AuthUser user, @PathVariable long id,
                                          @RequestBody StatusRequest body) {
        guard.require(user);
        service.setStatus(id, body.status());
        return ResponseEntity.noContent().build();
    }
}
