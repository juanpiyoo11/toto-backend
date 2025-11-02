package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.model.ConversationMessage;
import ar.edu.uade.toto.toto_backend.model.ConversationSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio simple y eficiente para manejar memoria de conversaciones.
 * Usa memoria local (HashMap) sin persistencia.
 */
@Service
public class ConversationMemoryService {
    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    // Almacenamiento en memoria: userId -> ConversationSession
    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    // Timeout para considerar una conversación como "nueva" (5 minutos por defecto)
    private final long sessionTimeoutMs;

    public ConversationMemoryService(
            @Value("${conversation.timeout-minutes:5}") int timeoutMinutes
    ) {
        this.sessionTimeoutMs = timeoutMinutes * 60 * 1000L;
        log.info("ConversationMemoryService initialized with timeout: {} minutes", timeoutMinutes);
        
        // Limpieza periódica de sesiones viejas (cada 10 minutos)
        startCleanupTask();
    }

    /**
     * Obtiene o crea una sesión para un usuario.
     * Si existe una sesión activa (no expirada), la retorna.
     * Si no existe o está expirada, crea una nueva.
     */
    public ConversationSession getOrCreateSession(String userId) {
        ConversationSession session = sessions.get(userId);
        
        if (session != null && !session.isExpired(sessionTimeoutMs)) {
            log.debug("Using existing session for user: {}", userId);
            return session;
        }
        
        // Crear nueva sesión
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        session = new ConversationSession(sessionId);
        sessions.put(userId, session);
        log.info("Created new conversation session {} for user: {}", sessionId, userId);
        return session;
    }

    /**
     * Agrega un mensaje a la sesión del usuario.
     */
    public void addMessage(String userId, String role, String content) {
        ConversationSession session = getOrCreateSession(userId);
        session.addMessage(new ConversationMessage(role, content));
    }

    /**
     * Obtiene el historial de mensajes de un usuario.
     */
    public List<ConversationMessage> getHistory(String userId) {
        ConversationSession session = sessions.get(userId);
        if (session != null && !session.isExpired(sessionTimeoutMs)) {
            return session.getMessages();
        }
        return List.of();
    }

    /**
     * Limpia la sesión de un usuario (fuerza conversación nueva).
     */
    public void clearSession(String userId) {
        sessions.remove(userId);
        log.debug("Cleared session for user: {}", userId);
    }

    /**
     * Obtiene el ID de sesión actual de un usuario (para debugging).
     */
    public String getCurrentSessionId(String userId) {
        ConversationSession session = sessions.get(userId);
        if (session != null && !session.isExpired(sessionTimeoutMs)) {
            return session.getSessionId();
        }
        return null;
    }

    /**
     * Tarea de limpieza periódica de sesiones expiradas.
     */
    private void startCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10 * 60 * 1000); // 10 minutos
                    cleanupExpiredSessions();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("ConversationCleanup");
        cleanupThread.start();
    }

    private void cleanupExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, ConversationSession> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(sessionTimeoutMs)) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired conversation sessions", removed);
        }
    }

    /**
     * Obtiene estadísticas del servicio (para debugging).
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "activeSessions", sessions.size(),
            "timeoutMinutes", sessionTimeoutMs / 60000
        );
    }
}
