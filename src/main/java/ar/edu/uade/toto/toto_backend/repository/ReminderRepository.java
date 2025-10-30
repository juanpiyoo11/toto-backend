package ar.edu.uade.toto.toto_backend.repository;

import ar.edu.uade.toto.toto_backend.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByElderlyId(Long elderlyId);
    List<Reminder> findByElderlyIdAndActive(Long elderlyId, Boolean active);
}
