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
     * @param elderlyId The elderly person's ID
     * @param reminderType Optional filter by reminder type (MEDICATION, APPOINTMENT, EVENT)
     */
    @Transactional(readOnly = true)
    public List<ReminderDTO> getTodayReminders(Long elderlyId, Reminder.ReminderType reminderType) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        return reminderRepository.findByElderlyIdAndActive(elderlyId, true).stream()
                .filter(r -> {
                    LocalDateTime time = r.getReminderTime();
                    boolean inRange = time.isAfter(startOfDay) && time.isBefore(endOfDay);
                    boolean typeMatch = reminderType == null || r.getReminderType() == reminderType;
                    return inRange && typeMatch;
                })
                .map(ReminderDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
