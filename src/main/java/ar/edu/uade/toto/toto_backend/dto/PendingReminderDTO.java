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
                msg.append("Acordate de tomar ");
                if (reminder.getDosage() != null && !reminder.getDosage().isEmpty()) {
                    msg.append(reminder.getDosage()).append(" de ");
                }
                msg.append(reminder.getTitle());
                break;
                
            case APPOINTMENT:
                msg.append("Acordate que tenés ");
                msg.append(reminder.getTitle());
                
                LocalDateTime eventTime = reminder.getReminderTime();
                int hour = eventTime.getHour();
                String timeStr = hour + (eventTime.getMinute() > 0 ? " y " + eventTime.getMinute() : "");
                msg.append(" a las ").append(timeStr);
                
                if (reminder.getDoctor() != null && !reminder.getDoctor().isEmpty()) {
                    msg.append(" con ").append(reminder.getDoctor());
                }
                if (reminder.getLocation() != null && !reminder.getLocation().isEmpty()) {
                    msg.append(" en ").append(reminder.getLocation());
                }
                break;
                
            case EVENT:
                msg.append("Acordate que tenés ");
                msg.append(reminder.getTitle());
                
                eventTime = reminder.getReminderTime();
                hour = eventTime.getHour();
                timeStr = hour + (eventTime.getMinute() > 0 ? " y " + eventTime.getMinute() : "");
                msg.append(" a las ").append(timeStr);
                
                if (reminder.getLocation() != null && !reminder.getLocation().isEmpty()) {
                    msg.append(" en ").append(reminder.getLocation());
                }
                break;
        }
        
        if (reminder.getDescription() != null && !reminder.getDescription().isEmpty()) {
            msg.append(". ").append(reminder.getDescription());
        }
        
        return msg.toString();
    }
}
