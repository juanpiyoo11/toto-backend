package ar.edu.uade.toto.toto_backend.dto;

public class SendMessageRequest {
    public String to;          // número en formato internacional (con o sin '+')
    public String text;        // cuerpo del mensaje
    public Boolean previewUrl; // opcional (default false)
}
