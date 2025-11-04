package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.service.SpotifyService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class SpotifyController {

    private final SpotifyService spotify;

    public SpotifyController(SpotifyService spotify) {
        this.spotify = spotify;
    }

    @GetMapping("/api/spotify/status")
    public ResponseEntity<?> status() {
        try {
            return ResponseEntity.ok(spotify.getStatus());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }

    @PostMapping("/api/spotify/logout")
    public ResponseEntity<?> logout() {
        spotify.logout();
        return ResponseEntity.ok(Map.of("status","ok","message","Tokens limpiados. Volvé a conectar."));
    }

    @GetMapping("/api/spotify/login")
    public ResponseEntity<Void> login() {
        String state = UUID.randomUUID().toString();
        String url = spotify.buildAuthorizeUrl(state);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    @GetMapping("/spotify/callback")
    public ResponseEntity<?> callback(@RequestParam(required = false) String code,
                                      @RequestParam(required = false) String state,
                                      @RequestParam(required = false, name = "error") String error) {
        try {
            if (error != null) return ResponseEntity.badRequest().body(Map.of("status","error","error", error));
            if (code == null || code.isBlank()) return ResponseEntity.badRequest().body(Map.of("status","error","error","missing_code"));
            spotify.exchangeCodeForTokens(code);
            return ResponseEntity.ok(Map.of("status","ok","message","Spotify conectado"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }

    @GetMapping("/api/spotify/devices")
    public ResponseEntity<?> devices() {
        try {
            JsonNode d = spotify.listDevices();
            return ResponseEntity.ok(d);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }

    @PostMapping("/api/spotify/play")
    public ResponseEntity<?> play(@RequestBody Map<String, String> body) {
        String deviceId = body.getOrDefault("deviceId", null);
        String query    = body.getOrDefault("query", null);
        String uri      = body.getOrDefault("uri", null);
        try {
            spotify.play(deviceId, query, uri);
            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "error" : e.getMessage();
            String code;
            if (msg.contains("PREMIUM_REQUIRED")) code = "PREMIUM_REQUIRED";
            else if (msg.contains("NO_DEVICE"))    code = "NO_DEVICE";
            else                                    code = "GENERIC";
            return ResponseEntity.status(500).body(Map.of("status","error","error", msg, "code", code));
        }
    }

    @PostMapping("/api/spotify/pause")
    public ResponseEntity<?> pause() {
        try {
            spotify.pause();
            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }

    @PostMapping("/api/spotify/next")
    public ResponseEntity<?> next() {
        try {
            spotify.nextTrack();
            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }

    @PostMapping("/api/spotify/previous")
    public ResponseEntity<?> previous() {
        try {
            spotify.previousTrack();
            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status","error","error", e.getMessage()));
        }
    }
}
