package ar.edu.uade.toto.toto_backend.repository;

import ar.edu.uade.toto.toto_backend.entity.CareRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {
    List<CareRelationship> findByCaregiverId(Long caregiverId);
    List<CareRelationship> findByElderlyId(Long elderlyId);
    boolean existsByCaregiverIdAndElderlyId(Long caregiverId, Long elderlyId);
}
