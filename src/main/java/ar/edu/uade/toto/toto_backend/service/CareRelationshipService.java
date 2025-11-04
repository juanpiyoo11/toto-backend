package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.CareRelationshipDTO;
import ar.edu.uade.toto.toto_backend.entity.CareRelationship;
import ar.edu.uade.toto.toto_backend.entity.User;
import ar.edu.uade.toto.toto_backend.exception.BadRequestException;
import ar.edu.uade.toto.toto_backend.exception.ResourceNotFoundException;
import ar.edu.uade.toto.toto_backend.repository.CareRelationshipRepository;
import ar.edu.uade.toto.toto_backend.repository.UserRepository;
import ar.edu.uade.toto.toto_backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareRelationshipService {

    private final CareRelationshipRepository careRelationshipRepository;
    private final UserRepository userRepository;

    @Transactional
    public CareRelationshipDTO createRelationship(CareRelationshipDTO dto, UserPrincipal userPrincipal) {
        // Verificar que el usuario actual sea un CAREGIVER
        User caregiver = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver not found"));

        if (!"CAREGIVER".equals(caregiver.getRole())) {
            throw new BadRequestException("Only caregivers can create care relationships");
        }

        // Verificar que el elderly exista
        User elderly = userRepository.findById(dto.getElderlyId())
                .orElseThrow(() -> new ResourceNotFoundException("Elderly user not found"));

        if (!"ELDERLY".equals(elderly.getRole())) {
            throw new BadRequestException("Target user must have ELDERLY role");
        }

        // Crear la relación
        CareRelationship relationship = new CareRelationship();
        relationship.setCaregiverId(userPrincipal.getId());
        relationship.setElderlyId(dto.getElderlyId());
        relationship.setRelationship(dto.getRelationship() != null ? dto.getRelationship() : "Cuidador");

        CareRelationship saved = careRelationshipRepository.save(relationship);
        return CareRelationshipDTO.fromEntity(saved);
    }

    @Transactional
    public void deleteRelationship(Long id, UserPrincipal userPrincipal) {
        CareRelationship relationship = careRelationshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Care relationship not found"));

        // Verificar que el usuario actual sea el caregiver de esta relación
        if (!relationship.getCaregiverId().equals(userPrincipal.getId())) {
            throw new BadRequestException("You can only delete your own care relationships");
        }

        careRelationshipRepository.delete(relationship);
    }
}
