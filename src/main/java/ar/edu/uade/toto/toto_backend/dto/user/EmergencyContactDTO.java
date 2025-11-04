package ar.edu.uade.toto.toto_backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyContactDTO {
    private Long id;
    private String name;
    private String phone;
    private String relationship;
}
