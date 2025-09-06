package ar.edu.uade.toto.toto_backend.dto;

public class SendMessageResponse {
    public String messageId;
    public String status;

    public SendMessageResponse(String messageId, String status) {
        this.messageId = messageId;
        this.status = status;
    }
}
