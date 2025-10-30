package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.dto.HistoryEventDTO;
import ar.edu.uade.toto.toto_backend.service.HistoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @Autowired
    private HistoryService historyService;

    @GetMapping
    public ResponseEntity<List<HistoryEventDTO>> getHistoryByUserId(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        if (start != null && end != null) {
            return ResponseEntity.ok(historyService.getHistoryByUserIdAndDateRange(userId, start, end));
        }
        return ResponseEntity.ok(historyService.getHistoryByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HistoryEventDTO> getHistoryEventById(@PathVariable Long id) {
        return ResponseEntity.ok(historyService.getHistoryEventById(id));
    }

    @PostMapping
    public ResponseEntity<HistoryEventDTO> createHistoryEvent(@Valid @RequestBody HistoryEventDTO dto) {
        return ResponseEntity.ok(historyService.createHistoryEvent(dto));
    }
}
