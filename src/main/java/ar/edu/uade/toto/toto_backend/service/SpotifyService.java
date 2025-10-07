package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.config.SpotifyProperties;
import ar.edu.uade.toto.toto_backend.spotify.SpotifyTokenStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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

    public String buildAuthorizeUrl(String state) {
        String base = "https://accounts.spotify.com/authorize";
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", props.getClientId());
        q.put("response_type", "code");
        q.put("redirect_uri", props.getRedirectUri());
        q.put("scope", props.getScopes());
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

    public void logout() { tokenStore.clear(); }

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
        return new Request.Builder().url(url).addHeader("Authorization", "Bearer " + at).method(method, body);
    }

    public JsonNode getCurrentUser() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me", "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("me HTTP " + resp.code() + " body=" + body);
            return mapper.readTree(body);
        }
    }

    public JsonNode listDevices() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/devices", "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("devices HTTP " + resp.code() + " body=" + body);
            return mapper.readTree(body);
        }
    }

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

    public void pause() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/pause", "PUT",
                RequestBody.create(new byte[0], null)).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("pause HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    public void nextTrack() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/next", "POST",
                RequestBody.create(new byte[0], null)).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("next HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    public void previousTrack() throws Exception {
        Request req = authed("https://api.spotify.com/v1/me/player/previous", "POST",
                RequestBody.create(new byte[0], null)).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("previous HTTP " + resp.code() + " body=" + body);
            }
        }
    }

    private record SearchResult(String contextUri, List<String> uris) {}
    private record Parsed(String track, String artist) {}

    public void play(String deviceId, String query, String uriOrContext) throws Exception {
        if (uriOrContext != null && !uriOrContext.isBlank()) {
            startPlayback(deviceId, uriOrContext, null);
            return;
        }
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Falta query o uri");
        SearchResult sr = searchBest(query);
        if (sr == null) throw new IllegalStateException("No encontré resultados para: " + query);
        startPlayback(deviceId, sr.contextUri(), sr.uris());
    }

    private SearchResult searchBest(String rawQuery) throws Exception {
        String artistFromGeneric = extractArtistFromGenericDe(rawQuery);
        if (artistFromGeneric != null) {
            SearchResult top = searchArtistTopTracks(artistFromGeneric, 10);
            if (top != null) return top;
            SearchResult ctx = searchArtistExactContext(artistFromGeneric);
            if (ctx != null) return ctx;
        }

        Parsed p = parseQuery(rawQuery);
        boolean genericTrackHint = isGenericTrackHint(p.track());

        if (p.artist() != null && !p.artist().isBlank()) {
            if (!genericTrackHint && p.track() != null && !p.track().isBlank()) {
                SearchResult exact = searchTrackExactUris("track:\"" + p.track() + "\" artist:\"" + p.artist() + "\"");
                if (exact != null) return exact;
                exact = searchTrackExactUris("artist:\"" + p.artist() + "\" track:\"" + p.track() + "\"");
                if (exact != null) return exact;
            }
            SearchResult top = searchArtistTopTracks(p.artist(), 10);
            if (top != null) return top;
            SearchResult ctx = searchArtistExactContext(p.artist());
            if (ctx != null) return ctx;
        }

        if (looksLikeArtistOnly(rawQuery)) {
            SearchResult top = searchArtistTopTracks(rawQuery, 10);
            if (top != null) return top;
            SearchResult ctx = searchArtistExactContext(rawQuery);
            if (ctx != null) return ctx;
        }

        SearchResult bestTrack = searchTrackBestByPopularity(rawQuery, 15);
        if (bestTrack != null) return bestTrack;

        return searchAny(rawQuery);
    }

    private SearchResult searchTrackExactUris(String q) throws Exception {
        String url = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&type=track&limit=5&market=from_token";
        Request req = authed(url, "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(Track) HTTP " + resp.code() + " body=" + body);
            JsonNode items = mapper.readTree(body).path("tracks").path("items");
            if (items.isArray() && items.size() > 0) {
                String uri = items.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(null, List.of(uri));
            }
            return null;
        }
    }

    private SearchResult searchArtistExactContext(String artistName) throws Exception {
        String q1 = "artist:\"" + artistName + "\"";
        String url1 = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(q1, StandardCharsets.UTF_8)
                + "&type=artist&limit=3&market=from_token";
        Request req1 = authed(url1, "GET", null).build();
        try (Response resp = http.newCall(req1).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(Artist) HTTP " + resp.code() + " body=" + body);
            JsonNode items = mapper.readTree(body).path("artists").path("items");
            if (items.isArray() && items.size() > 0) {
                String uri = items.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, null);
            }
        }
        String q2 = "\"" + artistName + "\"";
        String url2 = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(q2, StandardCharsets.UTF_8)
                + "&type=artist&limit=3&market=from_token";
        Request req2 = authed(url2, "GET", null).build();
        try (Response resp = http.newCall(req2).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(Artist2) HTTP " + resp.code() + " body=" + body);
            JsonNode items = mapper.readTree(body).path("artists").path("items");
            if (items.isArray() && items.size() > 0) {
                String uri = items.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, null);
            }
        }
        return null;
    }

    private SearchResult searchArtistTopTracks(String artistName, int limit) throws Exception {
        String url = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode("artist:\"" + artistName + "\"", StandardCharsets.UTF_8)
                + "&type=artist&limit=5&market=from_token";
        Request req = authed(url, "GET", null).build();
        String artistId = null;
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(ArtistTop) HTTP " + resp.code() + " body=" + body);
            JsonNode items = mapper.readTree(body).path("artists").path("items");
            if (items.isArray() && items.size() > 0) {
                String want = norm(artistName);
                int bestPop = -1;
                for (JsonNode it : items) {
                    String id = it.path("id").asText(null);
                    String nm = norm(it.path("name").asText(""));
                    int pop = it.path("popularity").asInt(0);
                    if (id == null) continue;
                    if (nm.equals(want)) { artistId = id; break; }
                    if (pop > bestPop) { bestPop = pop; artistId = id; }
                }
            }
        }
        if (artistId == null) return null;

        String ttUrl = "https://api.spotify.com/v1/artists/" + artistId + "/top-tracks?market=from_token";
        Request req2 = authed(ttUrl, "GET", null).build();
        try (Response resp = http.newCall(req2).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("top-tracks HTTP " + resp.code() + " body=" + body);
            JsonNode tracks = mapper.readTree(body).path("tracks");
            if (tracks.isArray() && tracks.size() > 0) {
                List<String> uris = new ArrayList<>();
                int n = Math.min(tracks.size(), Math.max(1, limit));
                for (int i = 0; i < n; i++) {
                    String uri = tracks.get(i).path("uri").asText(null);
                    if (uri != null) uris.add(uri);
                }
                if (!uris.isEmpty()) return new SearchResult(null, uris);
            }
        }
        return null;
    }

    private SearchResult searchTrackBestByPopularity(String rawQuery, int limit) throws Exception {
        String url = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(rawQuery, StandardCharsets.UTF_8)
                + "&type=track&limit=" + Math.max(5, limit) + "&market=from_token";
        Request req = authed(url, "GET", null).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IllegalStateException("search(TrackPop) HTTP " + resp.code() + " body=" + body);
            JsonNode items = mapper.readTree(body).path("tracks").path("items");
            if (!items.isArray() || items.size() == 0) return null;

            String want = cleanTitle(rawQuery);
            int bestIdx = -1, bestPop = -1;

            for (int i = 0; i < items.size(); i++) {
                JsonNode it = items.get(i);
                String name = cleanTitle(it.path("name").asText(""));
                int pop = it.path("popularity").asInt(0);
                if (name.equals(want) && pop > bestPop) { bestPop = pop; bestIdx = i; }
            }
            if (bestIdx < 0) {
                for (int i = 0; i < items.size(); i++) {
                    JsonNode it = items.get(i);
                    String name = cleanTitle(it.path("name").asText(""));
                    int pop = it.path("popularity").asInt(0);
                    if ((name.startsWith(want) || name.contains(want)) && pop > bestPop) { bestPop = pop; bestIdx = i; }
                }
            }
            if (bestIdx < 0) {
                for (int i = 0; i < items.size(); i++) {
                    int pop = items.get(i).path("popularity").asInt(0);
                    if (pop > bestPop) { bestPop = pop; bestIdx = i; }
                }
            }

            if (bestIdx >= 0) {
                String uri = items.get(bestIdx).path("uri").asText(null);
                if (uri != null) return new SearchResult(null, List.of(uri));
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
                if (uri != null) return new SearchResult(null, List.of(uri));
            }
            JsonNode playlists = j.path("playlists").path("items");
            if (playlists.isArray() && playlists.size() > 0) {
                String uri = playlists.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, null);
            }
            JsonNode albums = j.path("albums").path("items");
            if (albums.isArray() && albums.size() > 0) {
                String uri = albums.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, null);
            }
            JsonNode artists = j.path("artists").path("items");
            if (artists.isArray() && artists.size() > 0) {
                String uri = artists.get(0).path("uri").asText(null);
                if (uri != null) return new SearchResult(uri, null);
            }
            return null;
        }
    }

    private Parsed parseQuery(String raw) {
        if (raw == null) return new Parsed("", null);
        String s = raw.trim();
        String sl = s.toLowerCase(Locale.ROOT);
        String[] seps = {" - ", " – ", " — ", " de ", " by ", " del "};
        for (String sep : seps) {
            int idx = sl.lastIndexOf(sep);
            if (idx > 0) {
                String left = s.substring(0, idx).trim();
                String right = s.substring(idx + sep.length()).trim();
                if (!left.isEmpty() && !right.isEmpty()) return new Parsed(left, right);
            }
        }
        return new Parsed(s, null);
    }

    private void startPlayback(String deviceId, String contextUriOrTrackUri, List<String> urisIfTrack) throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        if (urisIfTrack != null && !urisIfTrack.isEmpty()) {
            payload.put("uris", urisIfTrack);
        } else {
            payload.put("context_uri", contextUriOrTrackUri);
        }
        String json = mapper.writeValueAsString(payload);

        HttpUrl.Builder url = Objects.requireNonNull(HttpUrl.parse("https://api.spotify.com/v1/me/player/play")).newBuilder();
        if (deviceId != null && !deviceId.isBlank()) url.addQueryParameter("device_id", deviceId);

        Request req = authed(url.build().toString(), "PUT",
                RequestBody.create(json, MediaType.get("application/json"))).build();

        try (Response resp = http.newCall(req).execute()) {
            if (resp.isSuccessful() || resp.code() == 204) return;

            String body = resp.body() != null ? resp.body().string() : "";
            int code = resp.code();

            if (isPremiumRequired(code, body)) throw new IllegalStateException("PREMIUM_REQUIRED: Premium requerido por Spotify (HTTP " + code + ").");

            if (isNoActiveDevice(code, body)) {
                String candidate = deviceId;
                if (candidate == null || candidate.isBlank()) candidate = pickBestDeviceId();
                if (candidate == null || candidate.isBlank())
                    throw new IllegalStateException("NO_DEVICE: No hay un dispositivo de Spotify disponible. Abrí Spotify en el teléfono/PC y reproducí algo una vez.");
                transferPlayback(candidate, true);

                HttpUrl retryUrl = Objects.requireNonNull(HttpUrl.parse("https://api.spotify.com/v1/me/player/play"))
                        .newBuilder().addQueryParameter("device_id", candidate).build();

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
            return b.contains("premium required");
        }
        return false;
    }

    private String pickBestDeviceId() throws Exception {
        JsonNode devices = listDevices();
        JsonNode arr = devices.path("devices");
        if (!arr.isArray() || arr.size() == 0) return null;
        for (JsonNode d : arr) if (d.path("is_active").asBoolean(false)) return d.path("id").asText(null);
        for (JsonNode d : arr) if (!d.path("is_restricted").asBoolean(false)) {
            String id = d.path("id").asText(null);
            if (id != null && !id.isBlank()) return id;
        }
        return arr.get(0).path("id").asText(null);
    }

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
            user.put("email", me.path("email").asText(null));
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
                for (JsonNode d : devices) {
                    if (d.path("is_active").asBoolean(false)) { suggested = d.path("id").asText(null); break; }
                }
                if (suggested == null && devices.size() > 0) suggested = devices.get(0).path("id").asText(null);
            }
            out.put("devices", dlist);
            out.put("suggestedDeviceId", suggested);
        } catch (Exception e) {
            out.put("error", "status_failed: " + e.getMessage());
        }
        return out;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String noAcc = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAcc.toLowerCase(Locale.ROOT).replaceAll("[“”\"']", "").replaceAll("\\s+", " ").trim();
    }

    private static boolean isGenericTrackHint(String s) {
        if (s == null || s.isBlank()) return true;
        String x = norm(s);
        x = x.replaceAll("^(pone|poneme|pones|poner|ponemos|ponele|reproduc[ei]r?|reproduce|reproduci|toca|tocame|pasa|pasame)\\s+", "");
        x = x.replaceAll("^(un|una|alguna|algun|alguno|algunos|algunas|algo|la|el)\\s+", "");
        if (x.isEmpty()) return true;
        String[] toks = x.split("\\s+");
        for (String t : toks) {
            if (!(t.equals("tema") || t.equals("temita") || t.equals("cancion") || t.equals("canción") || t.equals("musica") || t.equals("música"))) {
                return false;
            }
        }
        return true;
    }

    private static String cleanTitle(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s*\\([^)]*\\)\\s*", " ");
        s = s.replaceAll("\\s*\\[[^]]*\\]\\s*", " ");
        s = s.replaceAll("\\s*-\\s*.+$", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return norm(s);
    }

    private static String stripVerbsAndDet(String x) {
        if (x == null) return "";
        String s = x;
        s = s.replaceAll("^(pone|poneme|pones|poner|ponemos|reproduce|reproduc[ei]r?|reproduci|toca|tocame|pasa|pasame)\\s+", "");
        s = s.replaceAll("^(un|una|alguna|algun|alguno|algunos|algunas|algo|la|el)\\s+", "");
        return s.trim();
    }

    private static boolean looksLikeArtistOnly(String raw) {
        String x = stripVerbsAndDet(norm(raw));
        if (x.isEmpty()) return false;
        if (x.contains(" de ") || x.contains(" by ") || x.contains(" del ")) return false;
        if (x.contains(" tema ") || x.contains(" temita ") || x.contains(" cancion ") || x.contains(" canción ") ||
                x.contains(" musica ") || x.equals("musica") || x.equals("música")) return false;
        return x.split("\\s+").length <= 5;
    }

    private static String extractArtistFromGenericDe(String raw) {
        String n = norm(raw);
        int idx = n.lastIndexOf(" de ");
        if (idx <= 0) return null;
        String left = n.substring(0, idx).trim();
        String right = raw.substring(Math.min(raw.length(), idx + 4)).trim();
        left = left.replaceAll("^(pone|poneme|pones|poner|ponemos|reproduce|reproduci|toca|tocame|pasa|pasame)\\s+", "");
        left = left.replaceAll("^(un|una|alguna|algun|alguno|algunos|algunas|algo|la|el)\\s+", "");
        if (left.isEmpty()
                || left.matches("^(tema|temita|cancion|canción|musica|música)(\\s+.*)?$")) {
            return right;
        }
        return null;
    }
}
