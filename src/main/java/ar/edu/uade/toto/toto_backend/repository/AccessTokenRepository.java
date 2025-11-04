package ar.edu.uade.toto.toto_backend.repository;

import ar.edu.uade.toto.toto_backend.entity.AccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {
    
    Optional<AccessToken> findByTokenAndActiveTrue(String token);
    
    Optional<AccessToken> findByToken(String token);
    
    Optional<AccessToken> findByElderlyUserIdAndActive(Long elderlyUserId, boolean active);
    
    List<AccessToken> findByElderlyUserIdAndActiveTrue(Long elderlyUserId);
    
    List<AccessToken> findByCaregiverUserId(Long caregiverUserId);
    
    boolean existsByToken(String token);
}
