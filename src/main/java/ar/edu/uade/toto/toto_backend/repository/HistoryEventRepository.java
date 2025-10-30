package ar.edu.uade.toto.toto_backend.repository;

import ar.edu.uade.toto.toto_backend.entity.HistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoryEventRepository extends JpaRepository<HistoryEvent, Long> {
    List<HistoryEvent> findByUserIdOrderByTimestampDesc(Long userId);

    List<HistoryEvent> findByUserIdAndTimestampBetweenOrderByTimestampDesc(Long userId, LocalDateTime start,
            LocalDateTime end);
}
