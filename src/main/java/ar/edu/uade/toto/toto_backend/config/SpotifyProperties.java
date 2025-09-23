package ar.edu.uade.toto.toto_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
@ConfigurationProperties(prefix = "spotify")
public class SpotifyProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scopes = "user-modify-playback-state user-read-playback-state user-read-currently-playing user-read-private\n";

}
