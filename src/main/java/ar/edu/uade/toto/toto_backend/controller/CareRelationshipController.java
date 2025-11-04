package ar.edu.uade.toto.toto_backend.controller;

import ar.edu.uade.toto.toto_backend.dto.CareRelationshipDTO;
import ar.edu.uade.toto.toto_backend.security.UserPrincipal;
import ar.edu.uade.toto.toto_backend.service.CareRelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/care-relationships")
@RequiredArgsConstructor
public class CareRelationshipController {

    private final CareRelationshipService careRelationshipService;

    @PostMapping
    public ResponseEntity<CareRelationshipDTO> createRelationship(
            @RequestBody CareRelationshipDTO dto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        CareRelationshipDTO created = careRelationshipService.createRelationship(dto, userPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRelationship(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        careRelationshipService.deleteRelationship(id, userPrincipal);
        return ResponseEntity.noContent().build();
    }
}
