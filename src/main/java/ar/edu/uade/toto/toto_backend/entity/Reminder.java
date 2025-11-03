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
     * Calculates the effective reminder time considering lead time and repeat pattern.
     * For MEDICATION: returns the next occurrence time based on repeat pattern
     * For APPOINTMENT/EVENT: returns the next occurrence time - leadTimeMinutes
     */
    public LocalDateTime getEffectiveReminderTime() {
        LocalDateTime nextOccurrence = calculateNextOccurrence();
        
        if (nextOccurrence == null) {
            return null;
        }
        
        if (reminderType == ReminderType.MEDICATION) {
            return nextOccurrence;
        }
        
        // For appointments and events, subtract lead time
        int leadMinutes = (leadTimeMinutes != null && leadTimeMinutes > 0) ? leadTimeMinutes : 30;
        return nextOccurrence.minusMinutes(leadMinutes);
    }
    
    /**
     * Calculates the next occurrence of this reminder based on its repeat pattern.
     * For one-time reminders: returns reminderTime as-is
     * For recurring reminders: calculates the next occurrence from today
     */
    private LocalDateTime calculateNextOccurrence() {
        if (reminderTime == null) {
            return null;
        }
        
        String pattern = repeatPattern != null ? repeatPattern.toLowerCase() : "once";
        LocalDateTime now = LocalDateTime.now();
        
        // One-time reminder: use original time
        if ("once".equals(pattern)) {
            return reminderTime;
        }
        
        // For recurring reminders, calculate next occurrence
        LocalDateTime baseTime = reminderTime;
        
        if ("daily".equals(pattern)) {
            // Use today's date with the reminder's time
            LocalDateTime todayOccurrence = LocalDateTime.of(
                now.toLocalDate(),
                baseTime.toLocalTime()
            );
            
            // If today's time already passed, it's for tomorrow
            if (todayOccurrence.isBefore(now)) {
                return todayOccurrence.plusDays(1);
            }
            return todayOccurrence;
            
        } else if ("weekly".equals(pattern)) {
            // Find next occurrence of the same day of week
            LocalDateTime candidate = LocalDateTime.of(
                now.toLocalDate(),
                baseTime.toLocalTime()
            );
            
            while (candidate.getDayOfWeek() != baseTime.getDayOfWeek() || candidate.isBefore(now)) {
                candidate = candidate.plusDays(1);
            }
            return candidate;
            
        } else if ("monthly".equals(pattern)) {
            // Find next occurrence of the same day of month
            LocalDateTime candidate = LocalDateTime.of(
                now.getYear(),
                now.getMonth(),
                Math.min(baseTime.getDayOfMonth(), now.toLocalDate().lengthOfMonth()),
                baseTime.getHour(),
                baseTime.getMinute()
            );
            
            if (candidate.isBefore(now)) {
                // Try next month
                candidate = candidate.plusMonths(1);
                candidate = LocalDateTime.of(
                    candidate.getYear(),
                    candidate.getMonth(),
                    Math.min(baseTime.getDayOfMonth(), candidate.toLocalDate().lengthOfMonth()),
                    baseTime.getHour(),
                    baseTime.getMinute()
                );
            }
            return candidate;
            
        } else if ("yearly".equals(pattern)) {
            // Find next occurrence of the same month and day
            LocalDateTime candidate = LocalDateTime.of(
                now.getYear(),
                baseTime.getMonth(),
                baseTime.getDayOfMonth(),
                baseTime.getHour(),
                baseTime.getMinute()
            );
            
            if (candidate.isBefore(now)) {
                candidate = candidate.plusYears(1);
            }
            return candidate;
        }
        
        // Default: return original time
        return reminderTime;
    }
}

