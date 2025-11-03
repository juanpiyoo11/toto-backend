package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.dto.PendingReminderDTO;
import ar.edu.uade.toto.toto_backend.dto.ReminderDTO;
import ar.edu.uade.toto.toto_backend.entity.Reminder;
import ar.edu.uade.toto.toto_backend.service.ReminderNotificationService;
import ar.edu.uade.toto.toto_backend.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private ReminderNotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<ReminderDTO>> getRemindersByElderlyId(
            @RequestParam Long elderlyId,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        if (activeOnly) {
            return ResponseEntity.ok(reminderService.getActiveRemindersByElderlyId(elderlyId));
        }
        return ResponseEntity.ok(reminderService.getRemindersByElderlyId(elderlyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReminderDTO> getReminderById(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.getReminderById(id));
    }

    @PostMapping
    public ResponseEntity<ReminderDTO> createReminder(@Valid @RequestBody ReminderDTO dto) {
        return ResponseEntity.ok(reminderService.createReminder(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReminderDTO> updateReminder(@PathVariable Long id, @Valid @RequestBody ReminderDTO dto) {
        return ResponseEntity.ok(reminderService.updateReminder(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ReminderDTO> toggleReminderActive(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.toggleReminderActive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get pending reminders for an elderly person (used by toto-app for polling).
     * Returns reminders that should be announced now.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingReminderDTO>> getPendingReminders(@RequestParam Long elderlyId) {
        List<PendingReminderDTO> pending = notificationService.getPendingRemindersForElderly(elderlyId);
        return ResponseEntity.ok(pending);
    }

    /**
     * Mark a reminder as announced (called by toto-app after speaking the reminder).
     */
    @PostMapping("/{id}/announced")
    public ResponseEntity<Void> markReminderAnnounced(
            @PathVariable Long id,
            @RequestParam Long elderlyId) {
        notificationService.markReminderAnnounced(id, elderlyId);
        return ResponseEntity.ok().build();
    }

    /**
     * Record that a medication was taken.
     */
    @PostMapping("/{id}/taken")
    public ResponseEntity<Void> recordMedicationTaken(
            @PathVariable Long id,
            @RequestParam Long elderlyId,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        notificationService.recordMedicationTaken(id, elderlyId, notes);
        return ResponseEntity.ok().build();
    }

    /**
     * Record that a medication was skipped/not taken.
     */
    @PostMapping("/{id}/skipped")
    public ResponseEntity<Void> recordMedicationSkipped(
            @PathVariable Long id,
            @RequestParam Long elderlyId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        notificationService.recordMedicationSkipped(id, elderlyId, reason);
        return ResponseEntity.ok().build();
    }

    /**
     * Get today's reminders for an elderly person (for voice queries like "¿Qué medicamentos tengo hoy?").
     * @param elderlyId The elderly person's ID
     * @param type Optional filter by reminder type (medication, appointment, event)
     */
    @GetMapping("/today")
    public ResponseEntity<List<ReminderDTO>> getTodayReminders(
            @RequestParam Long elderlyId,
            @RequestParam(required = false) String type) {
        
        Reminder.ReminderType reminderType = null;
        if (type != null && !type.isEmpty()) {
            try {
                reminderType = Reminder.ReminderType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid type, ignore and return all
            }
        }
        
        return ResponseEntity.ok(reminderService.getTodayReminders(elderlyId, reminderType));
    }

    /**
     * Delete reminders by criteria (used for voice commands).
     * Example: DELETE /api/reminders/search?elderlyId=1&title=paracetamol&hour=18&type=medication
     */
    @DeleteMapping("/search")
    public ResponseEntity<Map<String, Object>> deleteRemindersByCriteria(
            @RequestParam Long elderlyId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false) Integer minute,
            @RequestParam(required = false) String type) {
        
        Reminder.ReminderType reminderType = null;
        if (type != null && !type.isEmpty()) {
            try {
                reminderType = Reminder.ReminderType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid type, ignore
            }
        }
        
        int deletedCount = reminderService.deleteRemindersByCriteria(elderlyId, title, hour, minute, reminderType);
        
        return ResponseEntity.ok(Map.of(
            "deletedCount", deletedCount,
            "message", deletedCount > 0 
                ? "Se eliminaron " + deletedCount + " recordatorio" + (deletedCount > 1 ? "s" : "")
                : "No se encontraron recordatorios con esos criterios"
        ));
    }
}

