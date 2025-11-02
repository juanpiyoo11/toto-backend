package ar.edu.uade.toto.toto_backend.model;

/**
 * Representa un mensaje individual en una conversación.
 */
public class ConversationMessage {
    private final String role;  // "user", "assistant", "system"
    private final String content;
    private final long timestamp;

    public ConversationMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
