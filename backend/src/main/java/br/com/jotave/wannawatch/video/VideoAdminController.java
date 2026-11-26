package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.shared.web.AdminGuard;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Teste dos vídeos do Reels: busca no YouTube e revisão. Protegido pelo X-Admin-Token. */
@RestController
@RequestMapping("/api/admin/videos")
public class VideoAdminController {

    private static final String TOKEN = "X-Admin-Token";

    private final AdminGuard guard;
    private final VideoDiscoveryService service;

    public VideoAdminController(AdminGuard guard, VideoDiscoveryService service) {
        this.guard = guard;
        this.service = service;
    }

    @PostMapping("/discover")
    public DiscoverResult discover(@RequestHeader(value = TOKEN, required = false) String token,
                                   @RequestBody(required = false) DiscoverRequest body) {
        guard.check(token);
        return body == null ? service.discover(null, null) : service.discover(body.limit(), body.titleIds());
    }

    @GetMapping
    public List<TitleVideos> list(@RequestHeader(value = TOKEN, required = false) String token) {
        guard.check(token);
        return service.list();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> setStatus(@RequestHeader(value = TOKEN, required = false) String token,
                                          @PathVariable long id, @RequestBody StatusRequest body) {
        guard.check(token);
        service.setStatus(id, body.status());
        return ResponseEntity.noContent().build();
    }
}
