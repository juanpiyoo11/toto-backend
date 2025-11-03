package ar.edu.uade.toto.toto_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminders", indexes = {
        @Index(name = "idx_elderly_reminder", columnList = "elderlyId"),
        @Index(name = "idx_reminder_time", columnList = "reminderTime"),
        @Index(name = "idx_reminder_type", columnList = "reminderType"),
        @Index(name = "idx_active_type", columnList = "active, reminderType")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {

    public enum ReminderType {
        MEDICATION,
        APPOINTMENT,
        EVENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long elderlyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "reminder_type")
    private ReminderType reminderType;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description; // Free-form notes, no longer used for structured data

    @Column(nullable = false)
    private LocalDateTime reminderTime; // For appointments/events: actual time of the event. For medication: time to take it.

    @Column(length = 20)
    private String repeatPattern; // e.g., "DAILY", "WEEKLY", "MONTHLY", "NONE"

    // Medication-specific fields
    @Column(length = 100)
    private String dosage;

    // Appointment-specific fields
    @Column(length = 100)
    private String doctor;

    // Appointment and Event fields
    @Column(length = 200)
    private String location;

    @Column(name = "lead_time_minutes")
    private Integer leadTimeMinutes; // Minutes before event to trigger reminder (default: 30 for appointments/events)

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Calculates the effective reminder time considering lead time.
     * For MEDICATION: returns reminderTime as-is
     * For APPOINTMENT/EVENT: returns reminderTime - leadTimeMinutes
     */
    public LocalDateTime getEffectiveReminderTime() {
        if (reminderType == ReminderType.MEDICATION) {
            return reminderTime;
        }
        
        // For appointments and events, subtract lead time
        int leadMinutes = (leadTimeMinutes != null && leadTimeMinutes > 0) ? leadTimeMinutes : 30; // default 30 min
        return reminderTime.minusMinutes(leadMinutes);
    }
}
