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
    private String description;

    @Column(nullable = false)
    private LocalDateTime reminderTime;

    @Column(length = 20)
    private String repeatPattern;

    @Column(length = 100)
    private String dosage;

    @Column(length = 100)
    private String doctor;

    @Column(length = 200)
    private String location;

    @Column(name = "lead_time_minutes")
    private Integer leadTimeMinutes;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    public LocalDateTime getEffectiveReminderTime() {
        LocalDateTime nextOccurrence = calculateNextOccurrence();
        
        if (nextOccurrence == null) {
            return null;
        }
        
        if (reminderType == ReminderType.MEDICATION) {
            return nextOccurrence;
        }

        int leadMinutes = (leadTimeMinutes != null && leadTimeMinutes > 0) ? leadTimeMinutes : 30;
        return nextOccurrence.minusMinutes(leadMinutes);
    }
    

    private LocalDateTime calculateNextOccurrence() {
        if (reminderTime == null) {
            return null;
        }
        
        String pattern = repeatPattern != null ? repeatPattern.toLowerCase() : "once";
        LocalDateTime now = LocalDateTime.now();
        

        if ("once".equals(pattern)) {
            return reminderTime;
        }
        

        LocalDateTime baseTime = reminderTime;
        
        if ("daily".equals(pattern)) {
            LocalDateTime todayOccurrence = LocalDateTime.of(
                now.toLocalDate(),
                baseTime.toLocalTime()
            );

            if (todayOccurrence.isBefore(now)) {
                return todayOccurrence.plusDays(1);
            }
            return todayOccurrence;
            
        } else if ("weekly".equals(pattern)) {
            LocalDateTime candidate = LocalDateTime.of(
                now.toLocalDate(),
                baseTime.toLocalTime()
            );
            
            while (candidate.getDayOfWeek() != baseTime.getDayOfWeek() || candidate.isBefore(now)) {
                candidate = candidate.plusDays(1);
            }
            return candidate;
            
        } else if ("monthly".equals(pattern)) {
            LocalDateTime candidate = LocalDateTime.of(
                now.getYear(),
                now.getMonth(),
                Math.min(baseTime.getDayOfMonth(), now.toLocalDate().lengthOfMonth()),
                baseTime.getHour(),
                baseTime.getMinute()
            );
            
            if (candidate.isBefore(now)) {
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

        return reminderTime;
    }
}

