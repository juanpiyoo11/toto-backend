package ar.edu.uade.toto.toto_backend.dto;

public class AskResponse {
    public String reply;
    public String sessionId;  // ID de sesión para debugging
    
    public AskResponse(String reply) { 
        this.reply = reply; 
    }
    
    public AskResponse(String reply, String sessionId) { 
        this.reply = reply;
        this.sessionId = sessionId;
    }
}
