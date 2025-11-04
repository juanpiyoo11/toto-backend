package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.ReminderDTO;
import ar.edu.uade.toto.toto_backend.entity.Reminder;
import ar.edu.uade.toto.toto_backend.exception.ResourceNotFoundException;
import ar.edu.uade.toto.toto_backend.repository.ReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

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

    @Transactional(readOnly = true)
    public List<ReminderDTO> getTodayReminders(Long elderlyId, Reminder.ReminderType reminderType, LocalDateTime targetDate) {
        LocalDateTime queryDate = targetDate != null ? targetDate : LocalDateTime.now();
        LocalDateTime startOfDay = queryDate.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        log.info("getTodayReminders: elderlyId={}, type={}, targetDate={}, queryDate={}, timezone={}", 
                 elderlyId, reminderType, targetDate, queryDate, java.util.TimeZone.getDefault().getID());
        
        return reminderRepository.findByElderlyIdAndActive(elderlyId, true).stream()
                .filter(r -> {
                    boolean typeMatch = reminderType == null || r.getReminderType() == reminderType;
                    if (!typeMatch) return false;
                    
                    LocalDateTime reminderTime = r.getReminderTime();
                    if (reminderTime == null) return false;
                    
                    String pattern = r.getRepeatPattern();
                    
                    if (pattern == null || "once".equalsIgnoreCase(pattern)) {
                        return reminderTime.isAfter(startOfDay) && reminderTime.isBefore(endOfDay);
                    } else if ("daily".equalsIgnoreCase(pattern)) {
                        return !reminderTime.toLocalDate().isAfter(queryDate.toLocalDate());
                    } else if ("weekly".equalsIgnoreCase(pattern)) {
                        boolean sameWeekday = reminderTime.getDayOfWeek() == queryDate.getDayOfWeek();
                        boolean startedBefore = !reminderTime.toLocalDate().isAfter(queryDate.toLocalDate());
                        return sameWeekday && startedBefore;
                    } else if ("monthly".equalsIgnoreCase(pattern)) {
                        boolean sameDayOfMonth = reminderTime.getDayOfMonth() == queryDate.getDayOfMonth();
                        boolean startedBefore = !reminderTime.toLocalDate().isAfter(queryDate.toLocalDate());
                        return sameDayOfMonth && startedBefore;
                    } else if ("yearly".equalsIgnoreCase(pattern)) {
                        boolean sameMonthDay = reminderTime.getMonth() == queryDate.getMonth() 
                                             && reminderTime.getDayOfMonth() == queryDate.getDayOfMonth();
                        boolean startedBefore = !reminderTime.toLocalDate().isAfter(queryDate.toLocalDate());
                        return sameMonthDay && startedBefore;
                    }
                    
                    return false;
                })
                .map(ReminderDTO::fromEntity)
                .collect(Collectors.toList());
    }


    @Transactional
    public int deleteRemindersByCriteria(Long elderlyId, String titleKeyword, Integer hour, Integer minute, Reminder.ReminderType reminderType) {
        List<Reminder> matches = reminderRepository.findByElderlyIdAndActive(elderlyId, true).stream()
                .filter(r -> {
                    boolean titleMatch = titleKeyword == null || 
                                         r.getTitle().toLowerCase().contains(titleKeyword.toLowerCase());
                    if (!titleMatch) return false;
                    
                    boolean typeMatch = reminderType == null || r.getReminderType() == reminderType;
                    if (!typeMatch) return false;
                    
                    if (hour != null && r.getReminderTime() != null) {
                        boolean hourMatch = r.getReminderTime().getHour() == hour;
                        if (!hourMatch) return false;
                        
                        if (minute != null) {
                            boolean minuteMatch = r.getReminderTime().getMinute() == minute;
                            if (!minuteMatch) return false;
                        }
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
        
        matches.forEach(reminderRepository::delete);
        
        return matches.size();
    }
}

