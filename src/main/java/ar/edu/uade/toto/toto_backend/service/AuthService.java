package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.auth.*;
import ar.edu.uade.toto.toto_backend.entity.User;
import ar.edu.uade.toto.toto_backend.exception.BadRequestException;
import ar.edu.uade.toto.toto_backend.repository.UserRepository;
import ar.edu.uade.toto.toto_backend.security.JwtTokenProvider;
import ar.edu.uade.toto.toto_backend.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ar.edu.uade.toto.toto_backend.repository.AccessTokenRepository accessTokenRepository;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal.getId());

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return new LoginResponse(accessToken, refreshToken, UserDTO.fromEntity(user));
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }

        if (!request.getRole().equals("ELDERLY") && !request.getRole().equals("CAREGIVER")) {
            throw new BadRequestException("Rol inválido. Debe ser ELDERLY o CAREGIVER");
        }

        if (request.getRole().equals("CAREGIVER")) {
            if (request.getEmail() == null || request.getPassword() == null) {
                throw new BadRequestException("CAREGIVER debe tener email y contraseña");
            }
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setAddress(request.getAddress());
        user.setBirthdate(request.getBirthdate());
        user.setMedicalInfo(request.getMedicalInfo());

        user = userRepository.save(user);

        if (request.getRole().equals("CAREGIVER") && request.getEmail() != null && request.getPassword() != null) {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            String accessToken = tokenProvider.generateAccessToken(authentication);
            String refreshToken = tokenProvider.generateRefreshToken(user.getId());

            return new LoginResponse(accessToken, refreshToken, UserDTO.fromEntity(user));
        } else {
            return new LoginResponse(null, null, UserDTO.fromEntity(user));
        }
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Token de actualización inválido o expirado");
        }

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String newAccessToken = tokenProvider.generateAccessToken(authentication);
        String newRefreshToken = tokenProvider.generateRefreshToken(userId);

        return new LoginResponse(newAccessToken, newRefreshToken, UserDTO.fromEntity(user));
    }

    @Transactional
    public LoginResponse loginWithToken(TokenLoginRequest request) {
        ar.edu.uade.toto.toto_backend.entity.AccessToken accessToken = accessTokenRepository
                .findByTokenAndActiveTrue(request.getToken())
                .orElseThrow(() -> new BadRequestException("Código inválido o inactivo"));

        accessToken.setLastUsedAt(java.time.LocalDateTime.now());
        accessTokenRepository.save(accessToken);

        User user = userRepository.findById(accessToken.getElderlyUserId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String jwtAccessToken = tokenProvider.generateAccessToken(authentication);
        String jwtRefreshToken = tokenProvider.generateRefreshToken(user.getId());

        return new LoginResponse(jwtAccessToken, jwtRefreshToken, UserDTO.fromEntity(user));
    }

    @Transactional
    public String generateAccessToken(Long elderlyUserId, Long caregiverUserId) {
        String token;
        do {
            token = String.format("%06d", (int) (Math.random() * 1000000));
        } while (accessTokenRepository.existsByToken(token));

        ar.edu.uade.toto.toto_backend.entity.AccessToken accessToken = new ar.edu.uade.toto.toto_backend.entity.AccessToken();
        accessToken.setToken(token);
        accessToken.setElderlyUserId(elderlyUserId);
        accessToken.setCaregiverUserId(caregiverUserId);
        accessToken.setActive(true);

        accessTokenRepository.save(accessToken);

        return token;
    }

    @Transactional
    public void deactivateAccessToken(String token) {
        ar.edu.uade.toto.toto_backend.entity.AccessToken accessToken = accessTokenRepository
                .findByTokenAndActiveTrue(token)
                .orElseThrow(() -> new BadRequestException("Token no encontrado"));

        accessToken.setActive(false);
        accessTokenRepository.save(accessToken);
    }

    public java.util.List<ar.edu.uade.toto.toto_backend.entity.AccessToken> getAccessTokensByElderly(Long elderlyUserId) {
        return accessTokenRepository.findByElderlyUserIdAndActiveTrue(elderlyUserId);
    }

    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        Object principal = authentication.getPrincipal();
        Long userId;
        
        if (principal instanceof UserPrincipal) {
            userId = ((UserPrincipal) principal).getId();
        } else if (principal instanceof String) {
            try {
                userId = Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                throw new BadRequestException("No se pudo identificar el usuario");
            }
        } else {
            throw new BadRequestException("Tipo de principal no soportado: " + principal.getClass().getName());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return UserDTO.fromEntity(user);
    }
}
