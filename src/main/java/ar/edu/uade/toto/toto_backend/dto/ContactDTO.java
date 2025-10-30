package ar.edu.uade.toto.toto_backend.dto;

import ar.edu.uade.toto.toto_backend.entity.Contact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {
    private Long id;

    @NotNull(message = "El ID del adulto mayor es obligatorio")
    private Long elderlyId;

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String relationship;

    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContactDTO fromEntity(Contact contact) {
        return new ContactDTO(
                contact.getId(),
                contact.getElderlyId(),
                contact.getName(),
                contact.getRelationship(),
                contact.getPhone(),
                contact.getCreatedAt(),
                contact.getUpdatedAt());
    }

    public Contact toEntity() {
        Contact contact = new Contact();
        contact.setId(this.id);
        contact.setElderlyId(this.elderlyId);
        contact.setName(this.name);
        contact.setRelationship(this.relationship);
        contact.setPhone(this.phone);
        return contact;
    }
}
