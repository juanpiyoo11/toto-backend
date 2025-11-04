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

    private static final int MAX_MESSAGES = 15;

    public ConversationSession(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.lastActivityTime = System.currentTimeMillis();
    }

    public void addMessage(ConversationMessage message) {
        messages.add(message);
        this.lastActivityTime = System.currentTimeMillis();

        if (messages.size() > MAX_MESSAGES) {
            ConversationMessage systemMsg = null;
            if (!messages.isEmpty() && "system".equals(messages.get(0).getRole())) {
                systemMsg = messages.get(0);
                messages.remove(0);
            }

            while (messages.size() >= MAX_MESSAGES) {
                messages.remove(0);
            }

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
