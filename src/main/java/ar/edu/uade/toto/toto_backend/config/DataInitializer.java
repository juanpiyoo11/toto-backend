package ar.edu.uade.toto.toto_backend.config;

import ar.edu.uade.toto.toto_backend.entity.AccessToken;
import ar.edu.uade.toto.toto_backend.entity.CareRelationship;
import ar.edu.uade.toto.toto_backend.entity.User;
import ar.edu.uade.toto.toto_backend.repository.AccessTokenRepository;
import ar.edu.uade.toto.toto_backend.repository.CareRelationshipRepository;
import ar.edu.uade.toto.toto_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initializeData() {
        return args -> {
            // Check if users already exist
            if (userRepository.existsByEmail("tamara.m94@hotmail.com")) {
                log.info("Initial data already exists, skipping initialization");
                return;
            }

            log.info("Initializing database with sample users and relationships...");

            // Create elderly user (Juan Pablo Yoo)
            User elderly = new User();
            elderly.setName("Juan Pablo Yoo");
            elderly.setEmail("tamara.m94@hotmail.com"); // Elderly users don't need email (use access token)
            elderly.setPassword(null); // Elderly users don't need password (use access token)
            elderly.setPhone("+5491158550932");
            elderly.setRole("ELDERLY");
            elderly.setAddress("Lima 757, CABA");
            elderly.setBirthdate("13/11/1950");
            elderly.setMedicalInfo("Hipertensión arterial, Alérgico a la penicilina. Contacto de Emergencia: Tamara Merchan");

            elderly = userRepository.save(elderly);
            log.info("Created elderly user: {}", elderly.getName());

            // Create caregiver user (Tamara Merchan)
            User caregiver = new User();
            caregiver.setName("Tamara Merchan");
            caregiver.setEmail("tamara.m94@hotmail.com");
            caregiver.setPassword(passwordEncoder.encode("tamara123"));
            caregiver.setPhone("+5491159753115");
            caregiver.setRole("CAREGIVER");
            caregiver.setAddress("Lima 575, CABA");
            caregiver.setBirthdate("01/09/1995");
            caregiver.setMedicalInfo(null);

            caregiver = userRepository.save(caregiver);
            log.info("Created caregiver user: {}", caregiver.getName());

            // Create care relationship
            CareRelationship relationship = new CareRelationship();
            relationship.setCaregiverId(caregiver.getId());
            relationship.setElderlyId(elderly.getId());
            relationship.setRelationship("Familiar"); // Could be "Hija", "Hijo", "Cuidador", etc.

            careRelationshipRepository.save(relationship);
            log.info("Created care relationship: {} -> {}", caregiver.getName(), elderly.getName());

            // Generate access token for elderly user (6-digit code)
            String token = "123456"; // Fixed token for testing
            AccessToken accessToken = new AccessToken();
            accessToken.setToken(token);
            accessToken.setElderlyUserId(elderly.getId());
            accessToken.setCaregiverUserId(caregiver.getId());
            accessToken.setActive(true);

            accessTokenRepository.save(accessToken);
            log.info("Created access token {} for elderly user {} (caregiver: {})", 
                    token, elderly.getName(), caregiver.getName());

            log.info("Database initialization completed successfully!");
            log.info("==================================================");
            log.info("Caregiver Login:");
            log.info("  Email: tamara.m94@hotmail.com");
            log.info("  Password: tamara123");
            log.info("==================================================");
            log.info("Elderly Access Token: {}", token);
            log.info("==================================================");
        };
    }
}
