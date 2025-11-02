package ar.edu.uade.toto.toto_backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa una sesión de conversación con su historial.
 */
public class ConversationSession {
    private final String sessionId;
    private final List<ConversationMessage> messages;
    private long lastActivityTime;

    // Límite de mensajes para mantener en memoria (eficiencia)
    private static final int MAX_MESSAGES = 15;

    public ConversationSession(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void addMessage(ConversationMessage message) {
        messages.add(message);
        this.lastActivityTime = System.currentTimeMillis();
        
        // Mantener solo los últimos MAX_MESSAGES (siempre guardamos el system prompt)
        // Dejamos el primero (system) y limpiamos los más viejos si excedemos
        if (messages.size() > MAX_MESSAGES) {
            // Guardar el system message si existe
            ConversationMessage systemMsg = null;
            if (!messages.isEmpty() && "system".equals(messages.get(0).getRole())) {
                systemMsg = messages.get(0);
                messages.remove(0);
            }
            
            // Remover los más viejos
            while (messages.size() >= MAX_MESSAGES) {
                messages.remove(0);
            }
            
            // Re-agregar system al inicio si existía
            if (systemMsg != null) {
                messages.add(0, systemMsg);
            }
        }
    }

    public List<ConversationMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isExpired(long timeoutMs) {
        return (System.currentTimeMillis() - lastActivityTime) > timeoutMs;
    }
}
