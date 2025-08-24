package ar.edu.uade.toto.toto_backend.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import ar.edu.uade.toto.toto_backend.dto.AskRequest;
import ar.edu.uade.toto.toto_backend.dto.AskResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final String OPENAI_URL = "https://api.openai.com/v1/responses";
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest body) throws IOException {
        String prompt = (body != null && body.prompt != null) ? body.prompt : "";

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            return new AskResponse("ERROR: Falta OPENAI_API_KEY en variables de entorno");
        }

        // Construimos el JSON de la request
        String reqJson = """
                {
                  "model": "gpt-4o-mini",
                  "input": [{"role":"user","content": %s}]
                }
                """.formatted(jsonEscape(prompt));

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(reqJson.getBytes(StandardCharsets.UTF_8),
                        okhttp3.MediaType.get("application/json")))
                .build();

        try (Response resp = http.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                return new AskResponse("HTTP " + resp.code() + " - " + resp.message());
            }

            String respStr = resp.body().string();

            // Parseamos la respuesta JSON
            JsonNode root = mapper.readTree(respStr);

            String text = "";
            JsonNode output = root.path("output");
            if (output.isArray() && output.size() > 0) {
                JsonNode content = output.get(0).path("content");
                if (content.isArray() && content.size() > 0) {
                    text = content.get(0).path("text").asText("");
                }
            }

            if (text.isEmpty()) {
                // fallback: devolver la respuesta cruda si no encontramos el texto
                text = respStr;
            }

            return new AskResponse(text);
        }
    }

    // util para escapar el prompt en JSON
    private static String jsonEscape(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

}
