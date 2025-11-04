package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.service.BootstrapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/dev")
public class DevController {

    @Autowired
    private BootstrapService bootstrapService;

    @Value("${dev.seed-key:local-dev-seed-key-12345}")
    private String seedKey;

    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap(
            @RequestHeader(value = "X-Dev-Seed-Key", required = false) String headerKey) {

        if (headerKey == null || !headerKey.equals(seedKey)) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Invalid or missing X-Dev-Seed-Key header");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        Map<String, Object> result = bootstrapService.seedDatabase();

        if ("skipped".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear(
            @RequestHeader(value = "X-Dev-Seed-Key", required = false) String headerKey) {

        if (headerKey == null || !headerKey.equals(seedKey)) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Invalid or missing X-Dev-Seed-Key header");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        Map<String, Object> result = bootstrapService.clearDatabase();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> devHealth() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Development endpoints are available");
        return ResponseEntity.ok(response);
    }
}
