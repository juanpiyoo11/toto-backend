package ar.edu.uade.toto.toto_backend.dto;

import ar.edu.uade.toto.toto_backend.entity.HistoryEvent;
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
public class HistoryEventDTO {
    private Long id;

    @NotNull(message = "El ID del usuario es obligatorio")
    private Long userId;

    @NotBlank(message = "El tipo de evento es obligatorio")
    private String eventType;

    private String details;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    public static HistoryEventDTO fromEntity(HistoryEvent event) {
        return new HistoryEventDTO(
                event.getId(),
                event.getUserId(),
                event.getEventType(),
                event.getDetails(),
                event.getTimestamp()
        );
    }

    public HistoryEvent toEntity() {
        HistoryEvent event = new HistoryEvent();
        event.setId(this.id);
        event.setUserId(this.userId);
        event.setEventType(this.eventType);
        event.setDetails(this.details);
        return event;
    }
}
