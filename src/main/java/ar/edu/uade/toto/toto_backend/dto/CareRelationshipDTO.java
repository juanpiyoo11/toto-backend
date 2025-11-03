package ar.edu.uade.toto.toto_backend.dto;

import ar.edu.uade.toto.toto_backend.entity.CareRelationship;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareRelationshipDTO {
    private Long id;
    private Long caregiverId;
    private Long elderlyId;
    private String relationship;
    private LocalDateTime createdAt;

    public static CareRelationshipDTO fromEntity(CareRelationship entity) {
        return new CareRelationshipDTO(
                entity.getId(),
                entity.getCaregiverId(),
                entity.getElderlyId(),
                entity.getRelationship(),
                entity.getCreatedAt()
        );
    }

    public CareRelationship toEntity() {
        CareRelationship entity = new CareRelationship();
        entity.setId(this.id);
        entity.setCaregiverId(this.caregiverId);
        entity.setElderlyId(this.elderlyId);
        entity.setRelationship(this.relationship);
        entity.setCreatedAt(this.createdAt);
        return entity;
    }
}
