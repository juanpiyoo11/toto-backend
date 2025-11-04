package ar.edu.uade.toto.toto_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class OpenAISTTService {

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String sttUrl;
    private final String sttModel;

    public OpenAISTTService(
            @Value("${openai.api-key:}") String apiKeyProp,
            @Value("${openai.audio-url:https://api.openai.com/v1/audio/transcriptions}") String sttUrl,
            @Value("${openai.stt-model:whisper-1}") String sttModel
    ) {
        String envKey = System.getenv("OPENAI_API_KEY");
        this.apiKey = (apiKeyProp != null && !apiKeyProp.isBlank())
                ? apiKeyProp
                : (envKey != null ? envKey : "");
        this.sttUrl = sttUrl;
        this.sttModel = sttModel;
    }

    public String transcribe(MultipartFile audio, String language) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Falta openai.api-key (o env OPENAI_API_KEY).");
        }

        MediaType octet = MediaType.parse("application/octet-stream");
        RequestBody fileBody = RequestBody.create(audio.getBytes(), octet);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", safeName(audio.getOriginalFilename()), fileBody)
                .addFormDataPart("model", sttModel)
                .addFormDataPart("language", language)
                .build();

        Request request = new Request.Builder()
                .url(sttUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response resp = http.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                String errBody = resp.body() != null ? resp.body().string() : "<no-body>";
                throw new IOException("HTTP " + resp.code() + ": " + errBody);
            }
            String json = resp.body().string();

            JsonNode root = mapper.readTree(json);
            String text = root.path("text").asText("");

            if (text.isBlank() && root.has("segments") && root.get("segments").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode seg : root.get("segments")) {
                    String t = seg.path("text").asText("");
                    if (!t.isBlank()) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(t.trim());
                    }
                }
                text = sb.toString();
            }

            return text;
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "audio.wav";
        return name.replaceAll("[\\r\\n\\t\\\\/]+", "_");
    }
}
