package ar.edu.uade.toto.toto_backend.repository;

import ar.edu.uade.toto.toto_backend.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByElderlyId(Long elderlyId);
}
