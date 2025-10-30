package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.ContactDTO;
import ar.edu.uade.toto.toto_backend.entity.Contact;
import ar.edu.uade.toto.toto_backend.exception.ResourceNotFoundException;
import ar.edu.uade.toto.toto_backend.repository.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContactService {

    @Autowired
    private ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public List<ContactDTO> getContactsByElderlyId(Long elderlyId) {
        return contactRepository.findByElderlyId(elderlyId).stream()
                .map(ContactDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContactDTO getContactById(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contacto", "id", id));
        return ContactDTO.fromEntity(contact);
    }

    @Transactional
    public ContactDTO createContact(ContactDTO dto) {
        Contact contact = dto.toEntity();
        contact = contactRepository.save(contact);
        return ContactDTO.fromEntity(contact);
    }

    @Transactional
    public ContactDTO updateContact(Long id, ContactDTO dto) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contacto", "id", id));

        contact.setName(dto.getName());
        contact.setRelationship(dto.getRelationship());
        contact.setPhone(dto.getPhone());

        contact = contactRepository.save(contact);
        return ContactDTO.fromEntity(contact);
    }

    @Transactional
    public void deleteContact(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contacto", "id", id));
        contactRepository.delete(contact);
    }
}
