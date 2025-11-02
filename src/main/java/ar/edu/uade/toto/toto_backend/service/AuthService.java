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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }

        if (!request.getRole().equals("ELDERLY") && !request.getRole().equals("CAREGIVER")) {
            throw new BadRequestException("Rol inválido. Debe ser ELDERLY o CAREGIVER");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setAddress(request.getAddress());
        user.setBirthdate(request.getBirthdate());
        user.setMedicalInfo(request.getMedicalInfo());

        user = userRepository.save(user);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return new LoginResponse(accessToken, refreshToken, UserDTO.fromEntity(user));
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

        // Update last used timestamp
        accessToken.setLastUsedAt(java.time.LocalDateTime.now());
        accessTokenRepository.save(accessToken);

        // Get elderly user
        User user = userRepository.findById(accessToken.getElderlyUserId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        // Generate JWT tokens
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String jwtAccessToken = tokenProvider.generateAccessToken(authentication);
        String jwtRefreshToken = tokenProvider.generateRefreshToken(user.getId());

        return new LoginResponse(jwtAccessToken, jwtRefreshToken, UserDTO.fromEntity(user));
    }

    @Transactional
    public String generateAccessToken(Long elderlyUserId, Long caregiverUserId) {
        // Generate 6-digit unique token
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
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        return UserDTO.fromEntity(user);
    }
}
