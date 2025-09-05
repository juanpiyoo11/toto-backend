package ar.edu.uade.toto.toto_backend.dto;

import java.util.Map;

public class NluRouteRequest {
    public String text;
    public String locale;                 // ej: "es-AR"
    public String tz;                     // ej: "America/Argentina/Buenos_Aires"
    public Long   now_epoch_ms;           // opcional (para tests/repro)

    public Map<String, Object> context;   // opcional (estado conversacional)
    public Map<String, Object> hints;     // opcional (pistas como contactos)
}
