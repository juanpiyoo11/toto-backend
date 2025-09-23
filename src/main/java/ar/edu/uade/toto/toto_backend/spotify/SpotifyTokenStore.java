package ar.edu.uade.toto.toto_backend.spotify;

import org.springframework.stereotype.Component;

@Component
public class SpotifyTokenStore {
    // Demo: un solo “perfil”
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile long   expiresAtMs; // epoch ms
    private volatile String tokenType;
    private volatile String scope;

    public synchronized void save(String accessToken, String refreshToken, int expiresInSec, String tokenType, String scope) {
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            this.refreshToken = refreshToken;
        }
        this.expiresAtMs = System.currentTimeMillis() + (expiresInSec - 30) * 1000L; // -30s margen
        this.tokenType = tokenType;
        this.scope = scope;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresAtMs() { return expiresAtMs; }
    public String getTokenType() { return tokenType; }
    public String getScope() { return scope; }

    public boolean hasTokens() { return refreshToken != null && !refreshToken.isBlank(); }
    public boolean isAccessTokenExpired() { return accessToken == null || System.currentTimeMillis() >= expiresAtMs; }

    public synchronized void clear() {
        accessToken = null; refreshToken = null; tokenType = null; scope = null; expiresAtMs = 0L;
    }
}
