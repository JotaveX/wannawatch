package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.auth.AdminGuard;
import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverRequest;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverResult;
import br.com.jotave.wannawatch.video.VideoDtos.StatusRequest;
import br.com.jotave.wannawatch.video.VideoDtos.TitleVideos;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Teste dos vídeos do Reels: busca no YouTube e revisão. Só para administradores. */
@RestController
@RequestMapping("/api/admin/videos")
public class VideoAdminController {

    private final AdminGuard guard;
    private final VideoDiscoveryService service;

    public VideoAdminController(AdminGuard guard, VideoDiscoveryService service) {
        this.guard = guard;
        this.service = service;
    }

    @PostMapping("/discover")
    public DiscoverResult discover(AuthUser user, @RequestBody(required = false) DiscoverRequest body) {
        guard.require(user);
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
