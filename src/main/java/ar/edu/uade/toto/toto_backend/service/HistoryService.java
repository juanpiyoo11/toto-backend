package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.HistoryEventDTO;
import ar.edu.uade.toto.toto_backend.entity.HistoryEvent;
import ar.edu.uade.toto.toto_backend.exception.ResourceNotFoundException;
import ar.edu.uade.toto.toto_backend.repository.HistoryEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    @Autowired
    private HistoryEventRepository historyEventRepository;

    @Transactional(readOnly = true)
    public List<HistoryEventDTO> getHistoryByUserId(Long userId) {
        return historyEventRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(HistoryEventDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HistoryEventDTO> getHistoryByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return historyEventRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, start, end).stream()
                .map(HistoryEventDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HistoryEventDTO getHistoryEventById(Long id) {
        HistoryEvent event = historyEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento de historial", "id", id));
        return HistoryEventDTO.fromEntity(event);
    }

    @Transactional
    public HistoryEventDTO createHistoryEvent(HistoryEventDTO dto) {
        HistoryEvent event = dto.toEntity();
        event = historyEventRepository.save(event);
        return HistoryEventDTO.fromEntity(event);
    }
}
