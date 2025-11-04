package ar.edu.uade.toto.toto_backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> out = new HashMap<>();
        out.put("status", "UP");
        out.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        try {
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            out.put("uptime_ms", uptime);
        } catch (Exception ignored) {
        }
        out.put("app", "toto-backend");
        return ResponseEntity.ok(out);
    }
}
