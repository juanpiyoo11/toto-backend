package ar.edu.uade.toto.toto_backend.dto;

import ar.edu.uade.toto.toto_backend.entity.Reminder;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderDTO {
    private Long id;

    @NotNull(message = "El ID del adulto mayor es obligatorio")
    private Long elderlyId;

    @NotNull(message = "El tipo de recordatorio es obligatorio")
    private Reminder.ReminderType reminderType;

    @NotBlank(message = "El título es obligatorio")
    private String title;

    private String description;

    @NotNull(message = "La fecha/hora del recordatorio es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime reminderTime;

    private String repeatPattern;

    private String dosage;

    private String doctor;

    private String location;
    private Integer leadTimeMinutes;

    private Boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReminderDTO fromEntity(Reminder reminder) {
        return new ReminderDTO(
                reminder.getId(),
                reminder.getElderlyId(),
                reminder.getReminderType(),
                reminder.getTitle(),
                reminder.getDescription(),
                reminder.getReminderTime(),
                reminder.getRepeatPattern(),
                reminder.getDosage(),
                reminder.getDoctor(),
                reminder.getLocation(),
                reminder.getLeadTimeMinutes(),
                reminder.getActive(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt());
    }

    public Reminder toEntity() {
        Reminder reminder = new Reminder();
        reminder.setId(this.id);
        reminder.setElderlyId(this.elderlyId);
        reminder.setReminderType(this.reminderType != null ? this.reminderType : Reminder.ReminderType.MEDICATION);
        reminder.setTitle(this.title);
        reminder.setDescription(this.description);
        reminder.setReminderTime(this.reminderTime);
        reminder.setRepeatPattern(this.repeatPattern);
        reminder.setDosage(this.dosage);
        reminder.setDoctor(this.doctor);
        reminder.setLocation(this.location);

        if (this.reminderType == Reminder.ReminderType.APPOINTMENT || this.reminderType == Reminder.ReminderType.EVENT) {
            reminder.setLeadTimeMinutes(this.leadTimeMinutes != null ? this.leadTimeMinutes : 30);
        } else {
            reminder.setLeadTimeMinutes(null);
        }
        
        reminder.setActive(this.active != null ? this.active : true);
        return reminder;
    }
}
