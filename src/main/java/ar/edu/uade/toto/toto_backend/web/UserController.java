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


    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getProfile() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }


    @PutMapping("/profile")
    public ResponseEntity<UserDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }


    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(userService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(userService.resetPassword(request));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteAccount() {
        return ResponseEntity.ok(userService.deleteAccount());
    }


    @GetMapping("/emergency-contacts")
    public ResponseEntity<List<EmergencyContactDTO>> getEmergencyContacts() {
        return ResponseEntity.ok(userService.getEmergencyContacts());
    }

    @GetMapping("/elderly-under-care")
    public ResponseEntity<List<UserDTO>> getElderlyUnderCare() {
        return ResponseEntity.ok(userService.getElderlyUnderCare());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserDTO> updateUserById(@PathVariable Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateUserById(userId, request));
    }

    @PostMapping("/elderly")
    public ResponseEntity<UserDTO> createElderly(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.createElderly(request));
    }

    @GetMapping("/{userId}/access-token")
    public ResponseEntity<Map<String, String>> getElderlyAccessToken(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getElderlyAccessToken(userId));
    }
}
