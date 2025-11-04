package ar.edu.uade.toto.toto_backend.dto.auth;

import ar.edu.uade.toto.toto_backend.validation.ValidateElderlyCredentials;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@ValidateElderlyCredentials
public class RegisterRequest {
    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String email;
    private String password;

    private String phone;

    @NotBlank(message = "El rol es obligatorio")
    private String role;

    private String birthdate;
    private String address;
    private String medicalInfo;
}
