package ar.edu.uade.toto.toto_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TokenLoginRequest {
    
    @NotBlank(message = "El token es obligatorio")
    @Pattern(regexp = "^[0-9]{6}$", message = "El token debe ser de 6 dígitos")
    private String token;
}
