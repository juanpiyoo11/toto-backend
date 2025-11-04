package ar.edu.uade.toto.toto_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "care_relationships", indexes = {
        @Index(name = "idx_caregiver", columnList = "caregiverId"),
        @Index(name = "idx_elderly", columnList = "elderlyId")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"caregiverId", "elderlyId"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caregiverId;

    @Column(nullable = false)
    private Long elderlyId;

    @Column(length = 50)
    private String relationship;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
