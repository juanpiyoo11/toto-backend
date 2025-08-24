package ar.edu.uade.toto.toto_backend.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.BufferedSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class ChatStreamController {

    private static final String OPENAI_URL = "https://api.openai.com/v1/responses";
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping(value = "/ask/stream", produces = "text/event-stream")
    public SseEmitter askStream(@RequestBody Prompt body) {
        String prompt = (body != null && body.prompt != null) ? body.prompt : "";
        SseEmitter emitter = new SseEmitter(0L); // 0L = sin timeout

        new Thread(() -> {
            try {
                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    emitter.send("data: ERROR: Falta OPENAI_API_KEY\n\n");
                    emitter.complete();
                    return;
                }

                String reqJson = """
                  {
                    "model": "gpt-4o-mini",
                    "stream": true,
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
                        emitter.send("data: HTTP " + resp.code() + " - " + resp.message() + "\n\n");
                        emitter.complete();
                        return;
                    }

                    BufferedSource source = resp.body().source();
                    while (!source.exhausted()) {
                        String line = source.readUtf8Line();
                        if (line == null) break;

                        if (line.startsWith("data: ")) {
                            String payload = line.substring(6).trim();
                            if ("[DONE]".equals(payload)) break;

                            try {
                                JsonNode node = mapper.readTree(payload);
                                JsonNode type = node.get("type");
                                if (type != null && "response.output_text.delta".equals(type.asText())) {
                                    String delta = node.get("delta").asText("");
                                    if (!delta.isEmpty()) {
                                        emitter.send("data: " + delta + "\n\n");
                                    }
                                }
                            } catch (Exception ignored) {
                                emitter.send("data: " + payload + "\n\n");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                try { emitter.send("data: ERROR: " + e.getMessage() + "\n\n"); } catch (Exception ignored) {}
            } finally {
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    // DTO simple
    public static class Prompt { public String prompt; }

    private static String jsonEscape(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
