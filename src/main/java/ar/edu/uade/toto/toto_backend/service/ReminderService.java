package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.ReminderDTO;
import ar.edu.uade.toto.toto_backend.entity.Reminder;
import ar.edu.uade.toto.toto_backend.exception.ResourceNotFoundException;
import ar.edu.uade.toto.toto_backend.repository.ReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReminderService {

    @Autowired
    private ReminderRepository reminderRepository;

    @Transactional(readOnly = true)
    public List<ReminderDTO> getRemindersByElderlyId(Long elderlyId) {
        return reminderRepository.findByElderlyId(elderlyId).stream()
                .map(ReminderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReminderDTO> getActiveRemindersByElderlyId(Long elderlyId) {
        return reminderRepository.findByElderlyIdAndActive(elderlyId, true).stream()
                .map(ReminderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReminderDTO getReminderById(Long id) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio", "id", id));
        return ReminderDTO.fromEntity(reminder);
    }

    @Transactional
    public ReminderDTO createReminder(ReminderDTO dto) {
        Reminder reminder = dto.toEntity();
        reminder = reminderRepository.save(reminder);
        return ReminderDTO.fromEntity(reminder);
    }

    @Transactional
    public ReminderDTO updateReminder(Long id, ReminderDTO dto) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio", "id", id));

        reminder.setTitle(dto.getTitle());
        reminder.setDescription(dto.getDescription());
        reminder.setReminderTime(dto.getReminderTime());
        reminder.setRepeatPattern(dto.getRepeatPattern());
        reminder.setReminderType(dto.getReminderType());
        reminder.setDosage(dto.getDosage());
        reminder.setDoctor(dto.getDoctor());
        reminder.setLocation(dto.getLocation());
        reminder.setLeadTimeMinutes(dto.getLeadTimeMinutes());
        reminder.setActive(dto.getActive());

        reminder = reminderRepository.save(reminder);
        return ReminderDTO.fromEntity(reminder);
    }

    @Transactional
    public void deleteReminder(Long id) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio", "id", id));
        reminderRepository.delete(reminder);
    }

    @Transactional
    public ReminderDTO toggleReminderActive(Long id) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio", "id", id));
        reminder.setActive(!reminder.getActive());
        reminder = reminderRepository.save(reminder);
        return ReminderDTO.fromEntity(reminder);
    }

    /**
     * Get all active reminders that should be triggered now or in the past.
     * This considers the effective reminder time (for appointments/events, it subtracts lead time).
     * Used by the scheduler to find reminders to trigger.
     */
    @Transactional(readOnly = true)
    public List<Reminder> getPendingReminders(LocalDateTime now) {
        List<Reminder> allActive = reminderRepository.findByActive(true);
        
        return allActive.stream()
                .filter(r -> {
                    LocalDateTime effectiveTime = r.getEffectiveReminderTime();
                    return effectiveTime != null && (effectiveTime.isBefore(now) || effectiveTime.isEqual(now));
                })
                .collect(Collectors.toList());
    }

    /**
     * Get active reminders for a specific elderly person for today.
     * Useful for voice queries like "¿Qué medicamentos tengo hoy?"
     * This method considers:
     * - Reminders scheduled for today
     * - Recurring reminders (daily, weekly, monthly, yearly) that apply today
     * @param elderlyId The elderly person's ID
     * @param reminderType Optional filter by reminder type (MEDICATION, APPOINTMENT, EVENT)
     */
    @Transactional(readOnly = true)
    public List<ReminderDTO> getTodayReminders(Long elderlyId, Reminder.ReminderType reminderType) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        return reminderRepository.findByElderlyIdAndActive(elderlyId, true).stream()
                .filter(r -> {
                    // Type filter
                    boolean typeMatch = reminderType == null || r.getReminderType() == reminderType;
                    if (!typeMatch) return false;
                    
                    LocalDateTime reminderTime = r.getReminderTime();
                    if (reminderTime == null) return false;
                    
                    // Check if reminder applies today based on repeat pattern
                    String pattern = r.getRepeatPattern();
                    
                    if (pattern == null || "once".equalsIgnoreCase(pattern)) {
                        // One-time reminder: check if it's today
                        return reminderTime.isAfter(startOfDay) && reminderTime.isBefore(endOfDay);
                    } else if ("daily".equalsIgnoreCase(pattern)) {
                        // Daily: applies today if reminder date is today or before today
                        return !reminderTime.toLocalDate().isAfter(now.toLocalDate());
                    } else if ("weekly".equalsIgnoreCase(pattern)) {
                        // Weekly: applies if same day of week and reminder date is today or before
                        boolean sameWeekday = reminderTime.getDayOfWeek() == now.getDayOfWeek();
                        boolean startedBefore = !reminderTime.toLocalDate().isAfter(now.toLocalDate());
                        return sameWeekday && startedBefore;
                    } else if ("monthly".equalsIgnoreCase(pattern)) {
                        // Monthly: applies if same day of month and reminder date is today or before
                        boolean sameDayOfMonth = reminderTime.getDayOfMonth() == now.getDayOfMonth();
                        boolean startedBefore = !reminderTime.toLocalDate().isAfter(now.toLocalDate());
                        return sameDayOfMonth && startedBefore;
                    } else if ("yearly".equalsIgnoreCase(pattern)) {
                        // Yearly: applies if same month and day, and reminder date is today or before
                        boolean sameMonthDay = reminderTime.getMonth() == now.getMonth() 
                                             && reminderTime.getDayOfMonth() == now.getDayOfMonth();
                        boolean startedBefore = !reminderTime.toLocalDate().isAfter(now.toLocalDate());
                        return sameMonthDay && startedBefore;
                    }
                    
                    return false;
                })
                .map(ReminderDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
