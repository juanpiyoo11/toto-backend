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

    private final Map<String, Long> resetTokens = new HashMap<>();

    @Transactional
    public UserDTO updateProfile(UpdateProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setBirthdate(request.getBirthdate());
        user.setMedicalInfo(request.getMedicalInfo());

        user = userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());

        return UserDTO.fromEntity(user);
    }

    @Transactional
    public Map<String, String> changePassword(ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", user.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Contraseña actualizada correctamente");
        return response;
    }

    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        String token = UUID.randomUUID().toString();
        resetTokens.put(token, user.getId());

        log.info("Password reset token generated for user: {}", user.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Token de recuperación generado");
        response.put("token", token);
        response.put("note", "En producción, este token se enviaría por email");

        return response;
    }

    @Transactional
    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        Long userId = resetTokens.get(request.getToken());
        if (userId == null) {
            throw new BadRequestException("Token inválido o expirado");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetTokens.remove(request.getToken());

        log.info("Password reset successful for user: {}", user.getEmail());

        Map<String, String> response = new HashMap<>();
        response.put("message", "Contraseña restablecida correctamente");
        return response;
    }

    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return UserDTO.fromEntity(user);
    }

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

    public List<EmergencyContactDTO> getEmergencyContacts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        List<EmergencyContactDTO> emergencyContacts = new ArrayList<>();
        
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

    public List<UserDTO> getElderlyUnderCare() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));
        
        if (!"CAREGIVER".equals(user.getRole())) {
            throw new BadRequestException("Solo los cuidadores pueden acceder a esta información");
        }
        
        List<CareRelationship> relationships = careRelationshipRepository.findByCaregiverId(userPrincipal.getId());
        
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

    @Transactional
    public UserDTO updateUserById(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setBirthdate(request.getBirthdate());
        user.setMedicalInfo(request.getMedicalInfo());

        user = userRepository.save(user);
        log.info("Profile updated for user ID: {}", userId);

        return UserDTO.fromEntity(user);
    }

    @Transactional
    public UserDTO createElderly(UpdateProfileRequest request) {
        log.info("Creating elderly user: {}", request.getName());

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

    private String generateSixDigitToken() {
        String token;
        do {
            int randomNum = (int) (Math.random() * 900000) + 100000;
            token = String.valueOf(randomNum);
        } while (accessTokenRepository.findByToken(token).isPresent());
        
        return token;
    }

    public Map<String, String> getElderlyAccessToken(Long elderlyId) {
        UserDTO currentUserDTO = getCurrentUser();
        
        if (!userRepository.existsById(elderlyId)) {
            throw new BadRequestException("Adulto mayor no encontrado");
        }
        
        boolean hasRelationship = careRelationshipRepository
                .existsByCaregiverIdAndElderlyId(currentUserDTO.getId(), elderlyId);
        
        if (!hasRelationship) {
            throw new BadRequestException("No tienes permiso para ver el token de este adulto mayor");
        }
        
        ar.edu.uade.toto.toto_backend.entity.AccessToken accessToken = accessTokenRepository
                .findByElderlyUserIdAndActive(elderlyId, true)
                .orElseThrow(() -> new BadRequestException("No se encontró un token activo para este adulto mayor"));
        
        return Map.of("token", accessToken.getToken());
    }
}
