package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.service.BootstrapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Development utilities controller.
 * Provides endpoints for database seeding and other dev operations.
 * These endpoints are protected by a custom header key.
 */
@RestController
@RequestMapping("/dev")
public class DevController {

    @Autowired
    private BootstrapService bootstrapService;

    @Value("${dev.seed-key:local-dev-seed-key-12345}")
    private String seedKey;

    /**
     * Seeds the database with initial test data.
     * Requires X-Dev-Seed-Key header matching the configured seed key.
     *
     * POST /dev/bootstrap
     * Header: X-Dev-Seed-Key: <your-seed-key>
     *
     * @param headerKey The seed key from request header
     * @return Map with seeding results
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap(
            @RequestHeader(value = "X-Dev-Seed-Key", required = false) String headerKey) {

        // Validate seed key
        if (headerKey == null || !headerKey.equals(seedKey)) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Invalid or missing X-Dev-Seed-Key header");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // Seed the database
        Map<String, Object> result = bootstrapService.seedDatabase();
        
        if ("skipped".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Clears all data from the database.
     * USE WITH EXTREME CAUTION!
     * Requires X-Dev-Seed-Key header matching the configured seed key.
     *
     * DELETE /dev/clear
     * Header: X-Dev-Seed-Key: <your-seed-key>
     *
     * @param headerKey The seed key from request header
     * @return Map with deletion results
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear(
            @RequestHeader(value = "X-Dev-Seed-Key", required = false) String headerKey) {

        // Validate seed key
        if (headerKey == null || !headerKey.equals(seedKey)) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Invalid or missing X-Dev-Seed-Key header");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // Clear the database
        Map<String, Object> result = bootstrapService.clearDatabase();
        return ResponseEntity.ok(result);
    }

    /**
     * Health check specifically for dev endpoints.
     *
     * @return Simple status message
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> devHealth() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Development endpoints are available");
        return ResponseEntity.ok(response);
    }
}
