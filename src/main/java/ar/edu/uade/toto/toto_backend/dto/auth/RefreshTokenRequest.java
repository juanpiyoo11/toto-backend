package ar.edu.uade.toto.toto_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "El refresh token es obligatorio")
    private String refreshToken;
}
