package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.entity.*;
import ar.edu.uade.toto.toto_backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for bootstrapping the database with initial test data.
 * Used for development and testing environments.
 */
@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private HistoryEventRepository historyEventRepository;

    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public Map<String, Object> seedDatabase() {
        Map<String, Object> result = new HashMap<>();

        long userCount = userRepository.count();
        if (userCount > 0) {
            log.info("Database already contains {} users. Skipping seed.", userCount);
            result.put("status", "skipped");
            result.put("message", "Database already contains data");
            result.put("existingUsers", userCount);
            return result;
        }

        log.info("Starting database seeding...");

        User caregiver = new User();
        caregiver.setName("Tamara Gonzales");
        caregiver.setEmail("tamara@toto.com");
        caregiver.setPassword(passwordEncoder.encode("Admin123!"));
        caregiver.setRole("CAREGIVER");
        caregiver.setPhone("+54 9 11 4444-5555");
        caregiver.setAddress("Av. Corrientes 1234, CABA, Argentina");
        caregiver = userRepository.save(caregiver);
        log.info("Created caregiver: {} (ID: {})", caregiver.getEmail(), caregiver.getId());

        User elderly = new User();
        elderly.setName("María González");
        elderly.setEmail("maria@toto.com");
        elderly.setPassword(passwordEncoder.encode("User123!"));
        elderly.setRole("ELDERLY");
        elderly.setPhone("+54 9 11 6666-7777");
        elderly.setAddress("Av. Corrientes 1234, CABA, Argentina");
        elderly.setBirthdate("15/03/1945");
        elderly.setMedicalInfo("Hipertensión controlada, diabetes tipo 2");
        elderly = userRepository.save(elderly);
        log.info("Created elderly user: {} (ID: {})", elderly.getEmail(), elderly.getId());

        CareRelationship relationship = new CareRelationship();
        relationship.setCaregiverId(caregiver.getId());
        relationship.setElderlyId(elderly.getId());
        relationship.setRelationship("Hija");
        relationship = careRelationshipRepository.save(relationship);
        log.info("Created care relationship: Caregiver {} -> Elderly {}", caregiver.getId(), elderly.getId());

        Contact contact = new Contact();
        contact.setElderlyId(elderly.getId());
        contact.setName("Dr. Roberto Pérez");
        contact.setRelationship("Médico de cabecera");
        contact.setPhone("+54 9 11 5555-1234");
        contact = contactRepository.save(contact);
        log.info("Created contact: {} for elderly {}", contact.getName(), elderly.getId());

        Reminder reminder = new Reminder();
        reminder.setElderlyId(elderly.getId());
        reminder.setTitle("Tomar medicación matutina");
        reminder.setDescription("Aspirina 100mg + Enalapril 10mg");
        reminder.setReminderTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0));
        reminder.setRepeatPattern("DAILY");
        reminder.setActive(true);
        reminder = reminderRepository.save(reminder);
        log.info("Created reminder: {} for elderly {}", reminder.getTitle(), elderly.getId());

        HistoryEvent event = new HistoryEvent();
        event.setUserId(elderly.getId());
        event.setEventType("MEDICATION_TAKEN");
        event.setDetails("Medicación matutina completada correctamente");
        event.setTimestamp(LocalDateTime.now().minusHours(2));
        event = historyEventRepository.save(event);
        log.info("Created history event: {} for elderly {}", event.getEventType(), elderly.getId());

        log.info("Database seeding completed successfully!");

        result.put("status", "success");
        result.put("message", "Database seeded successfully");
        result.put("usersCreated", 2);
        result.put("careRelationshipsCreated", 1);
        result.put("contactsCreated", 1);
        result.put("remindersCreated", 1);
        result.put("historyEventsCreated", 1);
        result.put("caregiverEmail", caregiver.getEmail());
        result.put("elderlyEmail", elderly.getEmail());
        result.put("defaultPassword", "Admin123! (caregiver) / User123! (elderly)");

        return result;
    }

    @Transactional
    public Map<String, Object> clearDatabase() {
        log.warn("Starting database clear operation...");

        long historyCount = historyEventRepository.count();
        long reminderCount = reminderRepository.count();
        long contactCount = contactRepository.count();
        long relationshipCount = careRelationshipRepository.count();
        long userCount = userRepository.count();

        historyEventRepository.deleteAll();
        reminderRepository.deleteAll();
        contactRepository.deleteAll();
        careRelationshipRepository.deleteAll();
        userRepository.deleteAll();

        log.warn("Database cleared. Deleted {} history events, {} reminders, {} contacts, {} relationships, {} users",
                historyCount, reminderCount, contactCount, relationshipCount, userCount);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Database cleared successfully");
        result.put("historyEventsDeleted", historyCount);
        result.put("remindersDeleted", reminderCount);
        result.put("contactsDeleted", contactCount);
        result.put("careRelationshipsDeleted", relationshipCount);
        result.put("usersDeleted", userCount);

        return result;
    }
}
