package ar.edu.uade.toto.toto_backend.dto.auth;

import ar.edu.uade.toto.toto_backend.validation.ValidateElderlyCredentials;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@ValidateElderlyCredentials
public class RegisterRequest {
    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    // Email y password validados por @ValidateElderlyCredentials según el rol
    private String email;
    private String password;

    private String phone;

    @NotBlank(message = "El rol es obligatorio")
    private String role; // ELDERLY or CAREGIVER

    private String birthdate;
    private String address;
    private String medicalInfo;
}
