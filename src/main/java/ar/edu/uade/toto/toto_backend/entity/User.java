package ar.edu.uade.toto.toto_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = true)
    private String email;

    @Column(nullable = true)
    private String password;

    @Column(length = 20)
    private String phone;

    @Column(length = 20, nullable = false)
    private String role; // ELDERLY or CAREGIVER

    @Column(length = 500)
    private String address;

    @Column(length = 10)
    private String birthdate; // DD/MM/YYYY format

    @Column(length = 1000)
    private String medicalInfo;

    @Column(length = 500)
    private String emergencyContact;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
