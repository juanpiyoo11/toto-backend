package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.PendingReminderDTO;
import ar.edu.uade.toto.toto_backend.entity.HistoryEvent;
import ar.edu.uade.toto.toto_backend.entity.Reminder;
import ar.edu.uade.toto.toto_backend.repository.HistoryEventRepository;
import ar.edu.uade.toto.toto_backend.repository.ReminderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing reminder notifications and tracking.
 * Coordinates with the scheduler to determine which reminders should be triggered.
 */
@Service
public class ReminderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReminderNotificationService.class);

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private HistoryEventRepository historyEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get pending reminders for a specific elderly person that haven't been announced yet today.
     * This is called by the Android app via polling.
     */
    @Transactional(readOnly = true)
    public List<PendingReminderDTO> getPendingRemindersForElderly(Long elderlyId) {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> activeReminders = reminderRepository.findByElderlyIdAndActive(elderlyId, true);
        
        List<PendingReminderDTO> pending = new ArrayList<>();
        
        for (Reminder reminder : activeReminders) {
            if (shouldTriggerReminder(reminder, now)) {
                pending.add(PendingReminderDTO.fromReminder(reminder));
            }
        }
        
        log.info("Found {} pending reminders for elderlyId={}", pending.size(), elderlyId);
        return pending;
    }

    /**
     * Determines if a reminder should be triggered based on its effective time and whether
     * it has already been announced today.
     * 
     * Logic:
     * - MEDICATION: Triggers exactly at reminderTime (no early window)
     * - APPOINTMENT/EVENT: Triggers at reminderTime - leadTimeMinutes (no early window)
     * - All types: Allow up to 60 minutes late for missed reminders
     */
    private boolean shouldTriggerReminder(Reminder reminder, LocalDateTime now) {
        LocalDateTime effectiveTime = reminder.getEffectiveReminderTime();
        
        // Calculate minutes difference
        long minutesUntil = ChronoUnit.MINUTES.between(now, effectiveTime);
        
        // For MEDICATION: trigger exactly at time (0 min early, up to 60 min late)
        // For APPOINTMENT/EVENT: trigger at effectiveTime (already includes leadTime subtraction)
        if (minutesUntil > 0) {
            // Not yet time - don't trigger
            return false;
        }
        
        if (minutesUntil < -60) {
            // More than 1 hour late - too late
            return false;
        }
        
        // Check if already announced today
        if (wasAnnouncedToday(reminder)) {
            return false;
        }
        
        return true;
    }

    /**
     * Check if a reminder was already announced today by looking at history events.
     */
    private boolean wasAnnouncedToday(Reminder reminder) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        List<HistoryEvent> todayEvents = historyEventRepository
                .findByUserIdAndEventTypeAndTimestampBetween(
                        reminder.getElderlyId(),
                        "REMINDER_ANNOUNCED",
                        startOfDay,
                        endOfDay
                );
        
        // Check if any event matches this reminder
        for (HistoryEvent event : todayEvents) {
            if (event.getDetails() != null && event.getDetails().contains("\"reminderId\":" + reminder.getId())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Mark a reminder as announced by creating a history event.
     * Auto-deactivates one-time reminders after announcement.
     * Called after the Android app confirms it has announced the reminder to the user.
     */
    @Transactional
    public void markReminderAnnounced(Long reminderId, Long elderlyId) {
        try {
            // Get the reminder to check its repeat pattern
            Reminder reminder = reminderRepository.findById(reminderId).orElse(null);
            
            ObjectNode details = objectMapper.createObjectNode();
            details.put("reminderId", reminderId);
            details.put("action", "announced");
            
            HistoryEvent event = new HistoryEvent();
            event.setUserId(elderlyId);
            event.setEventType("REMINDER_ANNOUNCED");
            event.setDetails(objectMapper.writeValueAsString(details));
            
            historyEventRepository.save(event);
            log.info("Marked reminder {} as announced for elderlyId={}", reminderId, elderlyId);
            
            // Auto-deactivate if it's a one-time reminder
            if (reminder != null && "once".equalsIgnoreCase(reminder.getRepeatPattern())) {
                reminder.setActive(false);
                reminderRepository.save(reminder);
                log.info("Auto-deactivated one-time reminder {} after announcement", reminderId);
            }
        } catch (Exception e) {
            log.error("Error marking reminder as announced: reminderId={}, elderlyId={}", reminderId, elderlyId, e);
        }
    }

    /**
     * Record that a medication was taken.
     * Called when the user confirms they took the medication.
     */
    @Transactional
    public void recordMedicationTaken(Long reminderId, Long elderlyId, String notes) {
        try {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("reminderId", reminderId);
            details.put("action", "taken");
            if (notes != null && !notes.isEmpty()) {
                details.put("notes", notes);
            }
            
            HistoryEvent event = new HistoryEvent();
            event.setUserId(elderlyId);
            event.setEventType("MEDICATION_TAKEN");
            event.setDetails(objectMapper.writeValueAsString(details));
            
            historyEventRepository.save(event);
            log.info("Recorded medication taken: reminderId={}, elderlyId={}", reminderId, elderlyId);
        } catch (Exception e) {
            log.error("Error recording medication taken: reminderId={}, elderlyId={}", reminderId, elderlyId, e);
        }
    }

    /**
     * Record that a medication was skipped/not taken.
     * Called when the user says they didn't take the medication.
     */
    @Transactional
    public void recordMedicationSkipped(Long reminderId, Long elderlyId, String reason) {
        try {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("reminderId", reminderId);
            details.put("action", "skipped");
            if (reason != null && !reason.isEmpty()) {
                details.put("reason", reason);
            }
            
            HistoryEvent event = new HistoryEvent();
            event.setUserId(elderlyId);
            event.setEventType("MEDICATION_SKIPPED");
            event.setDetails(objectMapper.writeValueAsString(details));
            
            historyEventRepository.save(event);
            log.info("Recorded medication skipped: reminderId={}, elderlyId={}", reminderId, elderlyId);
        } catch (Exception e) {
            log.error("Error recording medication skipped: reminderId={}, elderlyId={}", reminderId, elderlyId, e);
        }
    }

    /**
     * Update a reminder for the next occurrence based on its repeat pattern.
     * For DAILY reminders: add 1 day
     * For WEEKLY: add 7 days
     * For MONTHLY: add 1 month
     * For NONE: deactivate
     */
    @Transactional
    public void scheduleNextOccurrence(Long reminderId) {
        try {
            Reminder reminder = reminderRepository.findById(reminderId).orElse(null);
            if (reminder == null || !reminder.getActive()) {
                return;
            }
            
            String pattern = reminder.getRepeatPattern();
            if ("NONE".equals(pattern)) {
                // One-time reminder, deactivate after triggering
                reminder.setActive(false);
                reminderRepository.save(reminder);
                log.info("Deactivated one-time reminder: {}", reminderId);
                return;
            }
            
            LocalDateTime current = reminder.getReminderTime();
            LocalDateTime next = null;
            
            switch (pattern) {
                case "DAILY":
                    next = current.plusDays(1);
                    break;
                case "WEEKLY":
                    next = current.plusWeeks(1);
                    break;
                case "MONTHLY":
                    next = current.plusMonths(1);
                    break;
                default:
                    log.warn("Unknown repeat pattern '{}' for reminder {}", pattern, reminderId);
                    return;
            }
            
            if (next != null) {
                reminder.setReminderTime(next);
                reminderRepository.save(reminder);
                log.info("Scheduled next occurrence for reminder {}: {}", reminderId, next);
            }
        } catch (Exception e) {
            log.error("Error scheduling next occurrence for reminder {}", reminderId, e);
        }
    }
}
