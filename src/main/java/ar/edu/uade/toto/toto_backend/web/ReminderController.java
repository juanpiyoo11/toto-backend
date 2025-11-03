package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.dto.PendingReminderDTO;
import ar.edu.uade.toto.toto_backend.dto.ReminderDTO;
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
     */
    @GetMapping("/today")
    public ResponseEntity<List<ReminderDTO>> getTodayReminders(@RequestParam Long elderlyId) {
        return ResponseEntity.ok(reminderService.getTodayReminders(elderlyId));
    }
}
