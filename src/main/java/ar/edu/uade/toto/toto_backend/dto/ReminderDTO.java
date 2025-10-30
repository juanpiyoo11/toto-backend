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

    @NotBlank(message = "El título es obligatorio")
    private String title;

    private String description;

    @NotNull(message = "La fecha/hora del recordatorio es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime reminderTime;

    private String repeatPattern; // DAILY, WEEKLY, MONTHLY, NONE

    private Boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReminderDTO fromEntity(Reminder reminder) {
        return new ReminderDTO(
                reminder.getId(),
                reminder.getElderlyId(),
                reminder.getTitle(),
                reminder.getDescription(),
                reminder.getReminderTime(),
                reminder.getRepeatPattern(),
                reminder.getActive(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt());
    }

    public Reminder toEntity() {
        Reminder reminder = new Reminder();
        reminder.setId(this.id);
        reminder.setElderlyId(this.elderlyId);
        reminder.setTitle(this.title);
        reminder.setDescription(this.description);
        reminder.setReminderTime(this.reminderTime);
        reminder.setRepeatPattern(this.repeatPattern);
        reminder.setActive(this.active != null ? this.active : true);
        return reminder;
    }
}
