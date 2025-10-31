package ar.edu.uade.toto.toto_backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check and utility endpoints for connectivity testing.
 * These endpoints are public and don't require authentication.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/time")
    public ResponseEntity<Map<String, Object>> time() {
        Map<String, Object> response = new HashMap<>();
        ZonedDateTime now = ZonedDateTime.now();
        response.put("time", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        response.put("timezone", now.getZone().getId());
        response.put("timestamp", now.toInstant().toEpochMilli());
        return ResponseEntity.ok(response);
    }
}
