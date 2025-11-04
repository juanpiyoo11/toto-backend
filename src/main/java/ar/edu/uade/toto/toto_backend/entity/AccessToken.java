package ar.edu.uade.toto.toto_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_tokens", indexes = {
        @Index(name = "idx_token", columnList = "token"),
        @Index(name = "idx_elderly_user_id", columnList = "elderly_user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 6)
    private String token; // 6-digit numeric token

    @Column(name = "elderly_user_id", nullable = false)
    private Long elderlyUserId; // The elderly person this token belongs to

    @Column(name = "caregiver_user_id", nullable = false)
    private Long caregiverUserId; // The caregiver who generated this token

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastUsedAt;
}
