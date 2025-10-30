package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.ReminderDTO;
import ar.edu.uade.toto.toto_backend.entity.Reminder;
import ar.edu.uade.toto.toto_backend.exception.ResourceNotFoundException;
import ar.edu.uade.toto.toto_backend.repository.ReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
