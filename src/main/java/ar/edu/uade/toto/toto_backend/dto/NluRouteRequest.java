package ar.edu.uade.toto.toto_backend.dto;

import java.util.Map;

public class NluRouteRequest {
    public String text;
    public String locale;
    public String tz;
    public Long   now_epoch_ms;

    public Map<String, Object> context;
    public Map<String, Object> hints;
}
