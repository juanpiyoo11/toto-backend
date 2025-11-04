package ar.edu.uade.toto.toto_backend.dto;

import ar.edu.uade.toto.toto_backend.entity.Reminder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for reminders that need to be triggered/announced to the user.
 * Includes message text formatted for TTS.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingReminderDTO {
    private Long id;
    private Long elderlyId;
    private Reminder.ReminderType reminderType;
    private String title;
    private String ttsMessage; // Pre-formatted message for Text-to-Speech
    private LocalDateTime scheduledFor; // When it should have been announced
    private Boolean isMedication; // Quick flag for medication confirmation flow
    
    public static PendingReminderDTO fromReminder(Reminder reminder) {
        PendingReminderDTO dto = new PendingReminderDTO();
        dto.setId(reminder.getId());
        dto.setElderlyId(reminder.getElderlyId());
        dto.setReminderType(reminder.getReminderType());
        dto.setTitle(reminder.getTitle());
        dto.setScheduledFor(reminder.getEffectiveReminderTime());
        dto.setIsMedication(reminder.getReminderType() == Reminder.ReminderType.MEDICATION);
        
        // Format TTS message based on type
        dto.setTtsMessage(formatTtsMessage(reminder));
        
        return dto;
    }
    
    private static String formatTtsMessage(Reminder reminder) {
        StringBuilder msg = new StringBuilder();
        
        switch (reminder.getReminderType()) {
            case MEDICATION:
                msg.append("Recordatorio de medicamento: ");
                msg.append(reminder.getTitle());
                msg.append(". ¿Ya lo tomaste?");
                break;
                
            case APPOINTMENT:
                msg.append("Recordatorio de cita: ");
                msg.append(reminder.getTitle());
                
                // Add time for appointments
                LocalDateTime eventTime = reminder.getReminderTime();
                if (eventTime != null) {
                    int hour = eventTime.getHour();
                    int minute = eventTime.getMinute();
                    msg.append(" a las ").append(hour);
                    if (minute > 0) {
                        msg.append(" y ").append(minute);
                    }
                }
                break;
                
            case EVENT:
                msg.append("Recordatorio de evento: ");
                msg.append(reminder.getTitle());
                
                // Add time for events
                eventTime = reminder.getReminderTime();
                if (eventTime != null) {
                    int hour = eventTime.getHour();
                    int minute = eventTime.getMinute();
                    msg.append(" a las ").append(hour);
                    if (minute > 0) {
                        msg.append(" y ").append(minute);
                    }
                }
                break;
        }
        
        return msg.toString();
    }
}
