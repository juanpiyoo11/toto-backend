package ar.edu.uade.toto.toto_backend.dto.auth;

import ar.edu.uade.toto.toto_backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String address;
    private String birthdate;
    private String medicalInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserDTO fromEntity(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getAddress(),
                user.getBirthdate(),
                user.getMedicalInfo(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
