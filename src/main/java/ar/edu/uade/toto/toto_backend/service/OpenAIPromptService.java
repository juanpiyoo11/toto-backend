package ar.edu.uade.toto.toto_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class OpenAIPromptService {

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    // NUEVO: se leen desde application.yml
    private final String systemStyle;
    private final String defaultLocale;

    public OpenAIPromptService(
            @Value("${openai.api-key:}") String apiKeyProp,
            @Value("${openai.api-url:https://api.openai.com/v1/responses}") String apiUrl,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.system-style:}") String systemStyleProp,
            @Value("${openai.default-locale:es-AR}") String defaultLocale
    ) {
        String env = System.getenv("OPENAI_API_KEY");
        this.apiKey = (apiKeyProp != null && !apiKeyProp.isBlank()) ? apiKeyProp : (env != null ? env : "");
        this.apiUrl = apiUrl;
        this.model = model;

        this.defaultLocale = (defaultLocale == null || defaultLocale.isBlank()) ? "es-AR" : defaultLocale.trim();
        // Si no definiste system-style en YAML, armamos uno por defecto en base al locale
        this.systemStyle = (systemStyleProp != null && !systemStyleProp.isBlank())
                ? systemStyleProp
                : ("Respondé SIEMPRE en español (" + this.defaultLocale + "). " +
                "Usá un tono rioplatense, claro y breve para lectura en voz alta. " +
                "Usá formato 24 h y fechas DD/MM/AAAA.");
    }

    /** Respuesta no-stream */
    public String ask(String prompt) throws IOException {
        ensureApiKey();

        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.7);
        root.put("stream", false);
        root.put("max_output_tokens", 80);
        root.set("input", buildInput(prompt));

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                // .addHeader("OpenAI-Beta", "responses-2024-12-17") // úsalo si tu cuenta lo requiere
                .post(RequestBody.create(mapper.writeValueAsBytes(root), MediaType.get("application/json")))
                .build();

        try (Response resp = http.newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + " - " + body);
            JsonNode r = mapper.readTree(body);
            String text = extractOutputText(r);
            return (text == null || text.isBlank()) ? body : text;
        }
    }


    /** Construye el array de mensajes input: system (desde YAML) + user */
    private ArrayNode buildInput(String prompt) {
        ArrayNode input = mapper.createArrayNode();

        ObjectNode sys = mapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemStyle);
        ObjectNode usr = mapper.createObjectNode();
        usr.put("role", "user");
        usr.put("content", (prompt == null) ? "" : prompt);

        input.add(sys);
        input.add(usr);
        return input;
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Falta OPENAI_API_KEY (o property openai.api-key)");
        }
    }

    /** Extrae texto del esquema Responses API. */
    private String extractOutputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode content = output.get(0).path("content");
            if (content.isArray() && content.size() > 0) {
                String t = content.get(0).path("text").asText(null);
                if (t != null) return t;
            }
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText(null);
        }
        return null;
    }
}
