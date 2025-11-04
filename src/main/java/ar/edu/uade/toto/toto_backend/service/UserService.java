package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.auth.UserDTO;
import ar.edu.uade.toto.toto_backend.dto.user.ChangePasswordRequest;
import ar.edu.uade.toto.toto_backend.dto.user.EmergencyContactDTO;
import ar.edu.uade.toto.toto_backend.dto.user.ForgotPasswordRequest;
import ar.edu.uade.toto.toto_backend.dto.user.ResetPasswordRequest;
import ar.edu.uade.toto.toto_backend.dto.user.UpdateProfileRequest;
import ar.edu.uade.toto.toto_backend.entity.CareRelationship;
import ar.edu.uade.toto.toto_backend.entity.User;
import ar.edu.uade.toto.toto_backend.exception.BadRequestException;
import ar.edu.uade.toto.toto_backend.repository.CareRelationshipRepository;
import ar.edu.uade.toto.toto_backend.repository.UserRepository;
import ar.edu.uade.toto.toto_backend.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for user profile management operations.
 * Handles profile updates, password changes, and password reset functionality.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private CareRelationshipRepository careRelationshipRepository;

    @Autowired
    private ar.edu.uade.toto.toto_backend.repository.AccessTokenRepository accessTokenRepository;

    @Autowired
    private ar.edu.uade.toto.toto_backend.repository.ContactRepository contactRepository;

    // In-memory storage for password reset tokens (for simplicity)
    // In production, use Redis or database with expiration
    private final Map<String, Long> resetTokens = new HashMap<>();

    /**
     * Updates the current user's profile information.
     *
     * @param request The profile update request
     * @return Updated user DTO
     */
    @Transactional
    public UserDTO updateProfile(UpdateProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        // Update fields
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setBirthdate(request.getBirthdate());
        user.setMedicalInfo(request.getMedicalInfo());

        user = userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());

        return UserDTO.fromEntity(user);
    }

    /**
     * Changes the current user's password.
     * Requires the current password for security.
     *
     * @param request The password change request
     * @return Success message
     */
    @Transactional
    public Map<String, String> changePassword(ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", user.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Contraseña actualizada correctamente");
        return response;
    }

    /**
     * Initiates a password reset process by generating a reset token.
     * In a real application, this would send an email with the token.
     *
     * @param request The forgot password request
     * @return Message with reset token (for development/testing)
     */
    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        // Generate reset token
        String token = UUID.randomUUID().toString();
        resetTokens.put(token, user.getId());

        log.info("Password reset token generated for user: {}", user.getEmail());

        // In production, send email with reset link containing the token
        // For now, return the token in the response for testing
        Map<String, String> response = new HashMap<>();
        response.put("message", "Token de recuperación generado");
        response.put("token", token); // Remove this in production!
        response.put("note", "En producción, este token se enviaría por email");

        return response;
    }

    /**
     * Resets a user's password using a reset token.
     *
     * @param request The reset password request
     * @return Success message
     */
    @Transactional
    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        // Validate token
        Long userId = resetTokens.get(request.getToken());
        if (userId == null) {
            throw new BadRequestException("Token inválido o expirado");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Remove used token
        resetTokens.remove(request.getToken());

        log.info("Password reset successful for user: {}", user.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Contraseña restablecida correctamente");
        return response;
    }

    /**
     * Gets the current authenticated user.
     *
     * @return Current user DTO
     */
    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return UserDTO.fromEntity(user);
    }

    /**
     * Deletes the current user's account.
     * Use with caution!
     *
     * @return Success message
     */
    @Transactional
    public Map<String, String> deleteAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        userRepository.delete(user);
        log.warn("Account deleted for user: {}", user.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Cuenta eliminada correctamente");
        return response;
    }
    
    /**
     * Get emergency contacts (caregivers) for the current elderly user.
     * Now also includes trusted contacts from the contacts table.
     *
     * @return List of emergency contacts (caregivers + trusted contacts)
     */
    public List<EmergencyContactDTO> getEmergencyContacts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        List<EmergencyContactDTO> emergencyContacts = new ArrayList<>();
        
        // 1. Get all caregivers from care relationships
        List<CareRelationship> relationships = careRelationshipRepository.findByElderlyId(userPrincipal.getId());
        
        for (CareRelationship rel : relationships) {
            User caregiver = userRepository.findById(rel.getCaregiverId()).orElse(null);
            if (caregiver != null) {
                emergencyContacts.add(new EmergencyContactDTO(
                        caregiver.getId(),
                        caregiver.getName(),
                        caregiver.getPhone(),
                        rel.getRelationship()
                ));
            }
        }
        
        // 2. Get all trusted contacts from contacts table
        List<ar.edu.uade.toto.toto_backend.entity.Contact> trustedContacts = 
                contactRepository.findByElderlyId(userPrincipal.getId());
        
        for (ar.edu.uade.toto.toto_backend.entity.Contact contact : trustedContacts) {
            emergencyContacts.add(new EmergencyContactDTO(
                    contact.getId(),
                    contact.getName(),
                    contact.getPhone(),
                    contact.getRelationship()
            ));
        }
        
        return emergencyContacts;
    }
    
    /**
     * Get elderly persons associated with the current caregiver.
     *
     * @return List of elderly persons under care
     */
    public List<UserDTO> getElderlyUnderCare() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        // Verify user is a caregiver
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        if (!"CAREGIVER".equals(user.getRole())) {
            throw new BadRequestException("Solo los cuidadores pueden acceder a esta información");
        }
        
        // Get all care relationships where current user is the caregiver
        List<CareRelationship> relationships = careRelationshipRepository.findByCaregiverId(userPrincipal.getId());
        
        // Map to DTOs with elderly information
        return relationships.stream()
                .map(rel -> {
                    User elderly = userRepository.findById(rel.getElderlyId())
                            .orElse(null);
                    if (elderly == null) return null;
                    
                    return UserDTO.fromEntity(elderly);
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }
    
    /**
     * Updates any user's profile by ID.
     * Allows caregivers to update their elderly persons' profiles.
     *
     * @param userId The ID of the user to update
     * @param request The profile update request
     * @return Updated user DTO
     */
    @Transactional
    public UserDTO updateUserById(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        // Update fields
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setBirthdate(request.getBirthdate());
        user.setMedicalInfo(request.getMedicalInfo());

        user = userRepository.save(user);
        log.info("Profile updated for user ID: {}", userId);

        return UserDTO.fromEntity(user);
    }

    /**
     * Create a new elderly user without login credentials.
     * Only accessible by authenticated caregivers.
     *
     * @param request User profile data
     * @return Created user DTO
     */
    @Transactional
    public UserDTO createElderly(UpdateProfileRequest request) {
        log.info("Creating elderly user: {}", request.getName());

        // Get current authenticated user (caregiver)
        UserDTO caregiverDTO = getCurrentUser();
        User caregiver = userRepository.findById(caregiverDTO.getId())
                .orElseThrow(() -> new BadRequestException("Caregiver no encontrado"));

        User elderly = new User();
        elderly.setName(request.getName());
        elderly.setPhone(request.getPhone());
        elderly.setAddress(request.getAddress());
        elderly.setBirthdate(request.getBirthdate());
        elderly.setMedicalInfo(request.getMedicalInfo());
        elderly.setRole("ELDERLY");
        elderly.setEmail(null);
        elderly.setPassword(null);

        elderly = userRepository.save(elderly);
        log.info("Elderly user created with ID: {}", elderly.getId());

        // Generate 6-digit access token
        String token = generateSixDigitToken();
        ar.edu.uade.toto.toto_backend.entity.AccessToken accessToken = new ar.edu.uade.toto.toto_backend.entity.AccessToken();
        accessToken.setToken(token);
        accessToken.setElderlyUserId(elderly.getId());
        accessToken.setCaregiverUserId(caregiver.getId());
        accessToken.setActive(true);
        
        accessTokenRepository.save(accessToken);
        log.info("Generated access token {} for elderly user {}", token, elderly.getName());

        return UserDTO.fromEntity(elderly);
    }

    /**
     * Generate a unique 6-digit numeric token
     */
    private String generateSixDigitToken() {
        String token;
        do {
            int randomNum = (int) (Math.random() * 900000) + 100000; // Generate 6-digit number (100000-999999)
            token = String.valueOf(randomNum);
        } while (accessTokenRepository.findByToken(token).isPresent()); // Ensure uniqueness
        
        return token;
    }

    /**
     * Get access token for an elderly user.
     * Accessible by the caregiver who has a relationship with the elderly.
     *
     * @param elderlyId The ID of the elderly user
     * @return Map with the access token
     */
    public Map<String, String> getElderlyAccessToken(Long elderlyId) {
        UserDTO currentUserDTO = getCurrentUser();
        
        // Verify that the elderly exists
        if (!userRepository.existsById(elderlyId)) {
            throw new BadRequestException("Adulto mayor no encontrado");
        }
        
        // Verify that current user is a caregiver of this elderly
        boolean hasRelationship = careRelationshipRepository
                .existsByCaregiverIdAndElderlyId(currentUserDTO.getId(), elderlyId);
        
        if (!hasRelationship) {
            throw new BadRequestException("No tienes permiso para ver el token de este adulto mayor");
        }
        
        // Get the access token
        ar.edu.uade.toto.toto_backend.entity.AccessToken accessToken = accessTokenRepository
                .findByElderlyUserIdAndActive(elderlyId, true)
                .orElseThrow(() -> new BadRequestException("No se encontró un token activo para este adulto mayor"));
        
        return Map.of("token", accessToken.getToken());
    }
}
