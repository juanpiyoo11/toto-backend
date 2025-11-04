package ar.edu.uade.toto.toto_backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String phone;

    @Size(max = 500)
    private String address;

    private String birthdate;

    @Size(max = 1000)
    private String medicalInfo;
}
