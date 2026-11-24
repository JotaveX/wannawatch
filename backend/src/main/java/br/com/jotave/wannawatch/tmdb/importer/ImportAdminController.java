package br.com.jotave.wannawatch.tmdb.importer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparo manual da importação. Protegido pelo header X-Admin-Token (valor do ADMIN_TOKEN).
 * Sem ADMIN_TOKEN configurado, o endpoint responde 404 (fica desligado).
 */
@RestController
@RequestMapping("/api/admin/import")
public class ImportAdminController {

    private final CatalogImporter importer;
    private final ImportProperties props;

    public ImportAdminController(CatalogImporter importer, ImportProperties props) {
        this.importer = importer;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<?> start(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        ResponseEntity<ProblemDetail> denied = checkToken(token);
        if (denied != null) return denied;
        if (!importer.startAsync("endpoint de admin")) {
            return problem(HttpStatus.CONFLICT, "Já existe uma importação em andamento.");
        }
        return ResponseEntity.accepted().body(Map.of("status", "STARTED"));
    }

    @GetMapping
    public ResponseEntity<?> status(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        ResponseEntity<ProblemDetail> denied = checkToken(token);
        if (denied != null) return denied;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("running", importer.isRunning());
        body.put("lastResult", importer.lastResult().orElse(null));
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<ProblemDetail> checkToken(String token) {
        String expected = props.adminToken();
        if (expected == null || expected.isBlank()) {
            return problem(HttpStatus.NOT_FOUND, "Endpoint de admin desligado (ADMIN_TOKEN vazio).");
        }
        boolean ok = token != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        return ok ? null : problem(HttpStatus.UNAUTHORIZED, "X-Admin-Token inválido ou ausente.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
