package ar.edu.uade.toto.toto_backend.dto;

public class NluRouteResponse {
    public String intent;                 // CALL, SET_ALARM, QUERY_TIME, QUERY_DATE, OPEN_APP, SEND_MESSAGE, ANSWER, CANCEL, UNKNOWN
    public double confidence;
    public boolean needs_confirmation;

    public Slots slots = new Slots();     // nunca null
    public String clarifying_question;    // opcional
    public String ack_tts;                // opcional (back-end puede rellenar)
    public String safety_notes;           // opcional

    public static class Slots {
        public String  contact_query;
        public Integer hour;              // 0..23
        public Integer minute;            // 0..59
        public String  datetime_iso;      // ISO-8601 local (p.ej. 2025-09-01T07:30:00-03:00)
        public String  message_text;
        public String  app_name;
        
        // Reminder-specific fields
        public String  reminder_title;    // Núcleo del recordatorio sin palabras temporales
        public String  reminder_type;     // medication, appointment, event
        public String  repeat_pattern;    // once, daily, weekly, monthly
    }
}
