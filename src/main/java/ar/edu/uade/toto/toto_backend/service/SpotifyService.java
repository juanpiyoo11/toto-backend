package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.config.SpotifyProperties;
import ar.edu.uade.toto.toto_backend.spotify.SpotifyTokenStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SpotifyService {

    private final SpotifyProperties props;
    private final SpotifyTokenStore tokenStore;
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public SpotifyService(SpotifyProperties props, SpotifyTokenStore tokenStore) {
        this.props = props;
        this.tokenStore = tokenStore;
    }

    // ===== OAuth =====

    public String buildAuthorizeUrl(String state) {
        String base = "https://accounts.spotify.com/authorize";
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", props.getClientId());
        q.put("response_type", "code");
        q.put("redirect_uri", props.getRedirectUri()); // DEBE coincidir EXACTO con el del Dashboard
        q.put("scope", props.getScopes()); // user-modify-playback-state user-read-playback-state user-read-currently-playing
        q.put("state", state != null ? state : UUID.randomUUID().toString());

        StringBuilder sb = new StringBuilder(base).append('?');
        boolean first = true;
        for (var e : q.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public void exchangeCodeForTokens(String code) throws Exception {
        RequestBody form = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", props.getRedirectUri())
                .build();

        String basic = Credentials.basic(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8);

        Request req = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", basic)
                .post(form)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("Token exchange HTTP " + resp.code() + " body=" + body);
            JsonNode json = mapper.readTree(body);
            tokenStore.save(
                    json.path("access_token").asText(null),
                    json.path("refresh_token").asText(null),
                    json.path("expires_in").asInt(3600),
                    json.path("token_type").asText("Bearer"),
                    json.path("scope").asText("")
            );
        }
    }

    public void logout() {
        tokenStore.clear();
    }

    public void refreshIfNeeded() throws Exception {
        if (!tokenStore.hasTokens()) throw new IllegalStateException("No hay refresh token: primero hacé login con Spotify");
        if (!tokenStore.isAccessTokenExpired()) return;

        RequestBody form = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", tokenStore.getRefreshToken())
                .build();

        String basic = Credentials.basic(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8);

        Request req = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", basic)
                .post(form)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("Refresh HTTP " + resp.code() + " body=" + body);
            JsonNode json = mapper.readTree(body);
            tokenStore.save(
                    json.path("access_token").asText(null),
                    // refresh_token puede NO venir; mantenemos el anterior si no llega
                    json.path("refresh_token").asText(null),
                    json.path("expires_in").asInt(3600),
                    json.path("token_type").asText("Bearer"),
                    json.path("scope").asText(tokenStore.getScope() == null ? "" : tokenStore.getScope())
            );
        }
    }

    private Request.Builder authed(String url, String method, RequestBody body) throws Exception {
        refreshIfNeeded();
        String at = tokenStore.getAccessToken();
        if (at == null) throw new IllegalStateException("No access token");
        return new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + at)
                .method(method, body);
    }

    // ===== API helpers =====

    /** Perfil actual: útil para saber product (free/premium), id, email, etc. */
    public JsonNode getCurrentUser() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me", "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("me HTTP " + resp.code() + " body=" + body);
            return mapper.readTree(body);
        }
    }

    /** Devices del usuario autenticado. */
    public JsonNode listDevices() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/devices", "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("devices HTTP " + resp.code() + " body=" + body);
            return mapper.readTree(body);
        }
    }

    /** Transfer playback to deviceId (opcional). */
    public void transferPlayback(String deviceId, boolean play) throws Exception {
        String json = mapper.writeValueAsString(Map.of("device_ids", List.of(deviceId), "play", play));
        Request req = authed("https://api.spotify.com/v1/me/player", "PUT",
                RequestBody.create(json, MediaType.get("application/json"))).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("transfer HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    // ===== Playback simple =====

    public void pause() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/pause", "PUT", RequestBody.create(new byte[0], null)).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("pause HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    public void nextTrack() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/next", "POST", RequestBody.create(new byte[0], null)).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("next HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    public void previousTrack() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/previous", "POST", RequestBody.create(new byte[0], null)).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("previous HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    // ===== Play por query o por URI =====

    public void play(String deviceId, String query, String uriOrContext) throws Exception {
        if (uriOrContext != null && !uriOrContext.isBlank()) {
            startPlayback(deviceId, uriOrContext, null);
            return;
        }
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Falta query o uri");
        SearchResult sr = searchBest(query);
        if (sr == null) throw new IllegalStateException("No encontré resultados para: " + query);
        startPlayback(deviceId, sr.uri, sr.isTrack ? List.of(sr.uri) : null);
    }

    // ======== BÚSQUEDA MEJORADA ========

    private record SearchResult(String uri, boolean isTrack) {}
    private record Parsed(String track, String artist) {}

    private SearchResult searchBest(String rawQuery) throws Exception {
        Parsed p = parseQuery(rawQuery);

        // 1) Intento exacto: track + artist (con comillas)
        if (p.artist() != null && !p.artist().isBlank()) {
            String q1 = "track:\"" + p.track() + "\" artist:\"" + p.artist() + "\"";
            SearchResult sr = searchTrackExact(q1);
            if (sr != null) return sr;

            String q2 = "artist:\"" + p.artist() + "\" track:\"" + p.track() + "\"";
            sr = searchTrackExact(q2);
            if (sr != null) return sr;
        }

        // 2) Solo track (type=track)
        SearchResult sr = searchTrackExact("\"" + rawQuery + "\"");
        if (sr != null) return sr;

        // 3) Último recurso: cualquier cosa (prefiere track si aparece)
        return searchAny(rawQuery);
    }

    private SearchResult searchTrackExact(String q) throws Exception {
        String url = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&type=track&limit=1&market=from_token";
        Request req = authed(url, "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(Track) HTTP " + resp.code() + " body=" + body);
            JsonNode items = mapper.readTree(body).path("tracks").path("items");
            if (items.isArray() && items.size() > 0) {
                String uri = items.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, true);
            }
            return null;
        }
    }

    private SearchResult searchAny(String q) throws Exception {
        String url = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&type=track,playlist,album,artist&limit=1&market=from_token";
        Request req = authed(url, "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(Any) HTTP " + resp.code() + " body=" + body);
            JsonNode j = mapper.readTree(body);

            JsonNode tracks = j.path("tracks").path("items");
            if (tracks.isArray() && tracks.size() > 0) {
                String uri = tracks.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, true);
            }
            JsonNode playlists = j.path("playlists").path("items");
            if (playlists.isArray() && playlists.size() > 0) {
                String uri = playlists.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, false);
            }
            JsonNode albums = j.path("albums").path("items");
            if (albums.isArray() && albums.size() > 0) {
                String uri = albums.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, false);
            }
            JsonNode artists = j.path("artists").path("items");
            if (artists.isArray() && artists.size() > 0) {
                String uri = artists.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, false);
            }
            return null;
        }
    }

    /** "mi todo de hillsong" → track="mi todo", artist="hillsong" */
    private Parsed parseQuery(String raw) {
        if (raw == null) return new Parsed("", null);
        String s = raw.trim();
        String sl = s.toLowerCase(Locale.ROOT);

        // separadores comunes entre tema y artista
        String[] seps = {" - ", " – ", " — ", " de ", " by ", " del "};
        for (String sep : seps) {
            int idx = sl.indexOf(sep.trim());
            if (idx > 0) {
                String left = s.substring(0, idx).trim();
                String right = s.substring(idx + sep.length()).trim();
                if (!left.isEmpty() && !right.isEmpty()) return new Parsed(left, right);
            }
        }
        return new Parsed(s, null);
    }

    // ===== Reproducción =====

    private void startPlayback(String deviceId, String contextUriOrTrackUri, List<String> urisIfTrack) throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        if (urisIfTrack != null && !urisIfTrack.isEmpty()) {
            payload.put("uris", urisIfTrack); // reproducir pista(s) exacta(s)
        } else {
            payload.put("context_uri", contextUriOrTrackUri); // playlist/album/artist
        }
        String json = mapper.writeValueAsString(payload);

        HttpUrl.Builder url = Objects.requireNonNull(HttpUrl.parse("https://api.spotify.com/v1/me/player/play")).newBuilder();
        if (deviceId != null && !deviceId.isBlank()) {
            url.addQueryParameter("device_id", deviceId);
        }

        // intento 1
        Request req = authed(url.build().toString(), "PUT",
                RequestBody.create(json, MediaType.get("application/json"))).build();

        try (Response resp = http.newCall(req).execute()) {
            if (resp.isSuccessful() || resp.code() == 204) return;

            String body = resp.body() != null ? resp.body().string() : "";
            int code = resp.code();

            if (isPremiumRequired(code, body)) {
                throw new IllegalStateException("PREMIUM_REQUIRED: Premium requerido por Spotify (HTTP " + code + ").");
            }

            if (isNoActiveDevice(code, body)) {
                String candidate = deviceId;
                if (candidate == null || candidate.isBlank()) {
                    candidate = pickBestDeviceId();
                }
                if (candidate == null || candidate.isBlank()) {
                    throw new IllegalStateException("NO_DEVICE: No hay un dispositivo de Spotify disponible. Abrí Spotify en el teléfono/PC y reproducí algo una vez.");
                }
                transferPlayback(candidate, true);

                HttpUrl retryUrl = Objects.requireNonNull(HttpUrl.parse("https://api.spotify.com/v1/me/player/play"))
                        .newBuilder()
                        .addQueryParameter("device_id", candidate)
                        .build();

                Request retry = authed(retryUrl.toString(), "PUT",
                        RequestBody.create(json, MediaType.get("application/json"))).build();

                try (Response r2 = http.newCall(retry).execute()) {
                    if (!r2.isSuccessful() && r2.code() != 204) {
                        String b2 = r2.body() != null ? r2.body().string() : "";
                        throw new IllegalStateException("Reintento play falló (HTTP " + r2.code() + "): " + b2);
                    }
                    return;
                }
            }

            throw new IllegalStateException("Spotify play falló (HTTP " + code + "): " + body);
        }
    }

    private boolean isNoActiveDevice(int code, String body) {
        if (code == 404 || code == 403) {
            String b = body == null ? "" : body.toLowerCase(Locale.ROOT);
            return b.contains("no active device") || b.contains("device not found") || b.contains("player command failed");
        }
        return false;
    }

    private boolean isPremiumRequired(int code, String body) {
        if (code == 403) {
            String b = body == null ? "" : body.toLowerCase(Locale.ROOT);
            // Mensajes típicos: "Premium required", "Player command failed: Premium required"
            return b.contains("premium required");
        }
        return false;
    }

    /** Devuelve el mejor device disponible: primero activo; si no, el primero no restringido. */
    private String pickBestDeviceId() throws Exception {
        JsonNode devices = listDevices();
        JsonNode arr = devices.path("devices");
        if (!arr.isArray() || arr.size() == 0) return null;

        for (JsonNode d : arr) {
            if (d.path("is_active").asBoolean(false)) return d.path("id").asText(null);
        }
        for (JsonNode d : arr) {
            if (!d.path("is_restricted").asBoolean(false)) {
                String id = d.path("id").asText(null);
                if (id != null && !id.isBlank()) return id;
            }
        }
        return arr.get(0).path("id").asText(null);
    }

    // ====== STATUS (para diagnóstico) ======

    public Map<String, Object> getStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean connected = tokenStore.hasTokens();
        out.put("connected", connected);
        out.put("redirectUri", props.getRedirectUri());
        out.put("clientId", props.getClientId());

        if (!connected) {
            out.put("loginUrl", buildAuthorizeUrl(UUID.randomUUID().toString()));
            return out;
        }

        try {
            JsonNode me = getCurrentUser();
            String product = me.path("product").asText("");
            boolean premium = "premium".equalsIgnoreCase(product);

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", me.path("id").asText(null));
            user.put("display_name", me.path("display_name").asText(null));
            user.put("email", me.path("email").asText(null)); // puede venir null si tu app no pide user-read-email
            user.put("product", product);
            user.put("country", me.path("country").asText(null));

            out.put("user", user);
            out.put("premium", premium);

            JsonNode devices = listDevices().path("devices");
            out.put("deviceCount", devices.isArray() ? devices.size() : 0);

            List<Map<String, Object>> dlist = new ArrayList<>();
            String suggested = null;
            if (devices.isArray()) {
                for (JsonNode d : devices) {
                    Map<String, Object> di = new LinkedHashMap<>();
                    di.put("id", d.path("id").asText(null));
                    di.put("name", d.path("name").asText(null));
                    di.put("type", d.path("type").asText(null));
                    di.put("is_active", d.path("is_active").asBoolean(false));
                    di.put("is_restricted", d.path("is_restricted").asBoolean(false));
                    dlist.add(di);
                }
                // sugerido
                for (JsonNode d : devices) {
                    if (d.path("is_active").asBoolean(false)) {
                        suggested = d.path("id").asText(null);
                        break;
                    }
                }
                if (suggested == null && devices.size() > 0) {
                    suggested = devices.get(0).path("id").asText(null);
                }
            }
            out.put("devices", dlist);
            out.put("suggestedDeviceId", suggested);
        } catch (Exception e) {
            out.put("error", "status_failed: " + e.getMessage());
        }
        return out;
    }
}
