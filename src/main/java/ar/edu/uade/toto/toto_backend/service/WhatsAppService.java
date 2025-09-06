package ar.edu.uade.toto.toto_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String token;
    private final String phoneId;
    private final String graphVersion;

    // Normalización
    private final String defaultRegion;
    private final boolean drop9ForAr;

    // Template de apertura (1 parámetro: el texto)
    private final String openTemplateName;
    private final String openTemplateLang;

    /** (#131030) Recipient phone number not in allowed list */
    public static class RecipientNotAllowedException extends Exception {
        public RecipientNotAllowedException(String message) { super(message); }
    }

    /** Texto libre no permitido para iniciar conversación (fuera de 24h → requiere template) */
    public static class ConversationNotOpenException extends Exception {
        public ConversationNotOpenException(String message) { super(message); }
    }

    public WhatsAppService(
            @Value("${whatsapp.token:}") String token,
            @Value("${whatsapp.phone-id:}") String phoneId,
            @Value("${whatsapp.graph-version:v22.0}") String graphVersion,
            @Value("${whatsapp.default-region:AR}") String defaultRegion,
            @Value("${whatsapp.argentina.drop9:true}") boolean drop9ForAr,
            @Value("${whatsapp.template-name-open:open_text}") String openTemplateName,
            @Value("${whatsapp.template-language-code:es_AR}") String openTemplateLang
    ) {
        this.token = token;
        this.phoneId = phoneId;
        this.graphVersion = (graphVersion == null || graphVersion.isBlank()) ? "v22.0" : graphVersion.trim();
        this.defaultRegion = (defaultRegion == null || defaultRegion.isBlank()) ? "AR" : defaultRegion.trim().toUpperCase();
        this.drop9ForAr = drop9ForAr;
        this.openTemplateName = (openTemplateName == null || openTemplateName.isBlank()) ? "open_text" : openTemplateName.trim();
        this.openTemplateLang = (openTemplateLang == null || openTemplateLang.isBlank()) ? "es_AR" : openTemplateLang.trim();

        if (this.token == null || this.token.isBlank() || this.phoneId == null || this.phoneId.isBlank()) {
            log.warn("⚠️ WA_TOKEN o WA_PHONE_ID no configurados. Envío de WhatsApp deshabilitado.");
        } else {
            log.info("✅ WhatsAppService inicializado con phoneId={} (graph={})", this.phoneId, this.graphVersion);
        }
        log.info("☎️ Normalizador: defaultRegion={} | argentina.drop9={}", this.defaultRegion, this.drop9ForAr);
        log.info("🧩 Template apertura: name='{}' lang={}", this.openTemplateName, this.openTemplateLang);
    }

    /** Envía un mensaje de texto simple. Devuelve el messageId de WhatsApp. */
    public String sendText(String toRaw, String text, boolean previewUrl) throws Exception {
        if (token == null || token.isBlank() || phoneId == null || phoneId.isBlank()) {
            throw new IllegalStateException("WA_TOKEN/WA_PHONE_ID no configurados en variables de entorno.");
        }
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Texto vacío.");

        // Normalización robusta → dígitos E.164 SIN '+'
        String to = normalizeForWhatsApp(toRaw);
        if (to.isBlank()) throw new IllegalArgumentException("Número destino inválido: " + safe(toRaw));

        String url = "https://graph.facebook.com/" + graphVersion + "/" + phoneId + "/messages";

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "text");

        Map<String, Object> textNode = new HashMap<>();
        textNode.put("body", text);
        textNode.put("preview_url", previewUrl);
        payload.put("text", textNode);

        String json = mapper.writeValueAsString(payload);

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json.getBytes(StandardCharsets.UTF_8), JSON))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("❌ WhatsApp API {}: {}", resp.code(), body);
                // Mapeo de errores comunes
                try {
                    JsonNode root = mapper.readTree(body);
                    int code = root.path("error").path("code").asInt(0);
                    int sub  = root.path("error").path("error_subcode").asInt(0);
                    String msg = root.path("error").path("message").asText("");
                    String details = root.path("error").path("error_data").path("details").asText("");

                    if (code == 131030) {
                        throw new RecipientNotAllowedException(msg.isEmpty() ? "Recipient not allowed" : msg);
                    }

                    // Heurística: casos que requieren template para abrir conversación
                    String all = (msg + " " + details).toLowerCase();
                    boolean looks24h = code == 470 || code == 131047 || sub == 2018001
                            || all.contains("24 hour") || all.contains("24-hour")
                            || all.contains("fuera de 24") || all.contains("outside 24")
                            || all.contains("free-form") || all.contains("requires a template")
                            || all.contains("message template") || all.contains("hsm");

                    if (looks24h) throw new ConversationNotOpenException(msg.isEmpty() ? "Conversation not open" : msg);

                } catch (RecipientNotAllowedException | ConversationNotOpenException known) {
                    throw known;
                } catch (Exception ignore) { /* body no era JSON o formato distinto */ }

                throw new RuntimeException("WhatsApp API error " + resp.code() + ": " + body);
            }

            JsonNode root = mapper.readTree(body);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && messages.size() > 0) {
                String id = messages.get(0).path("id").asText("");
                log.info("✅ WhatsApp enviado a {} (id={})", mask(to), id);
                return id;
            }
            log.info("✅ WhatsApp enviado a {}, respuesta: {}", mask(to), body);
            return "";
        }
    }

    /** Envia template de apertura con 1 parámetro de cuerpo: el texto del mensaje. */
    public String sendTemplateOpenText(String toRaw, String textParam) throws Exception {
        if (token == null || token.isBlank() || phoneId == null || phoneId.isBlank()) {
            throw new IllegalStateException("WA_TOKEN/WA_PHONE_ID no configurados en variables de entorno.");
        }
        if (textParam == null || textParam.isBlank()) throw new IllegalArgumentException("Texto vacío (template).");

        String to = normalizeForWhatsApp(toRaw);
        if (to.isBlank()) throw new IllegalArgumentException("Número destino inválido: " + safe(toRaw));

        String url = "https://graph.facebook.com/" + graphVersion + "/" + phoneId + "/messages";

        // Payload template:
        // {
        //   "messaging_product": "whatsapp",
        //   "to": "5411...",
        //   "type": "template",
        //   "template": {
        //     "name": "<openTemplateName>",
        //     "language": { "code": "<openTemplateLang>" },
        //     "components": [
        //        { "type":"body", "parameters":[ { "type":"text", "text": textParam } ] }
        //     ]
        //   }
        // }
        Map<String, Object> bodyParam = new HashMap<>();
        bodyParam.put("type", "text");
        bodyParam.put("text", textParam);

        Map<String, Object> bodyComp = new HashMap<>();
        bodyComp.put("type", "body");
        bodyComp.put("parameters", new Object[]{ bodyParam });

        Map<String, Object> lang = new HashMap<>();
        lang.put("code", openTemplateLang);

        Map<String, Object> template = new HashMap<>();
        template.put("name", openTemplateName);
        template.put("language", lang);
        template.put("components", new Object[]{ bodyComp });

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");
        payload.put("template", template);

        String json = mapper.writeValueAsString(payload);

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json.getBytes(StandardCharsets.UTF_8), JSON))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("❌ WhatsApp TEMPLATE API {}: {}", resp.code(), body);
                try {
                    JsonNode root = mapper.readTree(body);
                    int code = root.path("error").path("code").asInt(0);
                    String msg = root.path("error").path("message").asText("");
                    if (code == 131030) throw new RecipientNotAllowedException(msg.isEmpty() ? "Recipient not allowed" : msg);
                } catch (RecipientNotAllowedException known) {
                    throw known;
                } catch (Exception ignore) {}
                throw new RuntimeException("WhatsApp TEMPLATE error " + resp.code() + ": " + body);
            }

            JsonNode root = mapper.readTree(body);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && messages.size() > 0) {
                String id = messages.get(0).path("id").asText("");
                log.info("✅ Template enviado a {} (id={})", mask(to), id);
                return id;
            }
            log.info("✅ Template enviado a {}, respuesta: {}", mask(to), body);
            return "";
        }
    }

    /**
     * Normaliza un número al formato que requiere la API de Meta:
     * - Parseo con libphonenumber (región por defecto configurable).
     * - Formateo a E.164 (+5411…).
     * - Regla opcional para Argentina: si drop9ForAr=true, +549→+54.
     * - Se remueve el '+' para cumplir con la API (quiere solo dígitos).
     */
    private String normalizeForWhatsApp(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber proto = util.parse(s, defaultRegion);
            if (!util.isValidNumber(proto)) throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid");

            String e164 = util.format(proto, PhoneNumberFormat.E164); // ej: +54911... o +5411...
            if (drop9ForAr && e164.startsWith("+549")) {
                e164 = "+54" + e164.substring(4); // quita el '9'
            }
            return e164.startsWith("+") ? e164.substring(1) : e164;
        } catch (NumberParseException e) {
            // Fallback simple
            String digits = s.replaceAll("[^0-9]", "");
            if ("AR".equalsIgnoreCase(defaultRegion)) {
                if (drop9ForAr && digits.startsWith("549")) digits = "54" + digits.substring(3);
                if (!digits.startsWith("54") && (digits.length() == 10 || digits.length() == 11)) {
                    digits = "54" + digits.replaceFirst("^0+", "");
                }
            }
            return digits;
        }
    }

    private static String mask(String n) {
        if (n == null || n.isEmpty()) return "****";
        int keep = Math.min(4, n.length());
        return "****" + n.substring(n.length() - keep);
    }

    private static String safe(String s) { return (s == null) ? "" : s; }
}
