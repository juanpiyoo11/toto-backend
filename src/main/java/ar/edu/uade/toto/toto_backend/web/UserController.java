package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.dto.auth.UserDTO;
import ar.edu.uade.toto.toto_backend.dto.user.ChangePasswordRequest;
import ar.edu.uade.toto.toto_backend.dto.user.EmergencyContactDTO;
import ar.edu.uade.toto.toto_backend.dto.user.ForgotPasswordRequest;
import ar.edu.uade.toto.toto_backend.dto.user.ResetPasswordRequest;
import ar.edu.uade.toto.toto_backend.dto.user.UpdateProfileRequest;
import ar.edu.uade.toto.toto_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for user profile and account management.
 * All endpoints (except password reset ones) require authentication.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Get current user profile.
     * GET /api/user/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getProfile() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    /**
     * Update current user's profile.
     * PUT /api/user/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    /**
     * Change current user's password.
     * Requires current password for security.
     * PUT /api/user/password
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(request));
    }

    /**
     * Request password reset token.
     * Public endpoint - no authentication required.
     * POST /api/user/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(userService.forgotPassword(request));
    }

    /**
     * Reset password using token.
     * Public endpoint - no authentication required.
     * POST /api/user/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(userService.resetPassword(request));
    }

    /**
     * Delete current user's account.
     * USE WITH CAUTION - This is irreversible!
     * DELETE /api/user/account
     */
    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteAccount() {
        return ResponseEntity.ok(userService.deleteAccount());
    }

    /**
     * Get emergency contacts for current elderly user.
     * Returns list of caregivers associated with this elderly user.
     * GET /api/user/emergency-contacts
     */
    @GetMapping("/emergency-contacts")
    public ResponseEntity<List<EmergencyContactDTO>> getEmergencyContacts() {
        return ResponseEntity.ok(userService.getEmergencyContacts());
    }
    
    /**
     * Get elderly persons under care of current caregiver.
     * Only accessible by users with CAREGIVER role.
     * GET /api/user/elderly-under-care
     */
    @GetMapping("/elderly-under-care")
    public ResponseEntity<List<UserDTO>> getElderlyUnderCare() {
        return ResponseEntity.ok(userService.getElderlyUnderCare());
    }

    /**
     * Update any user's profile by ID.
     * Allows caregivers to update elderly profiles or update their own profile.
     * PUT /api/user/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserDTO> updateUserById(@PathVariable Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateUserById(userId, request));
    }

    /**
     * Create a new elderly user (without login credentials).
     * Only accessible by authenticated caregivers.
     * POST /api/user/elderly
     */
    @PostMapping("/elderly")
    public ResponseEntity<UserDTO> createElderly(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.createElderly(request));
    }
}
