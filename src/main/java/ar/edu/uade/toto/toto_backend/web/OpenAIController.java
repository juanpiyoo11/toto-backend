package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.dto.AskRequest;
import ar.edu.uade.toto.toto_backend.dto.AskResponse;
import ar.edu.uade.toto.toto_backend.service.OpenAIPromptService;
import ar.edu.uade.toto.toto_backend.service.OpenAISTTService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class OpenAIController {

    private final OpenAISTTService stt;
    private final OpenAIPromptService prompts;

    public OpenAIController(OpenAISTTService stt, OpenAIPromptService prompts) {
        this.stt = stt;
        this.prompts = prompts;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest body) {
        String prompt = (body != null && body.prompt != null) ? body.prompt : "";
        try {
            String reply = prompts.ask(prompt);   // delega en el service
            return new AskResponse(reply);
        } catch (Exception e) {
            return new AskResponse("ERROR: " + e.getMessage());
        }
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AskRequest body) {
        String prompt = (body != null && body.prompt != null) ? body.prompt : "";
        SseEmitter emitter = new SseEmitter(0L);  // sin timeout
        prompts.askStream(emitter, prompt);       // el service escribe en el emitter (SSE)
        return emitter;
    }

    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> transcribe(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false, defaultValue = "es") String language
    ) throws Exception {
        String text = stt.transcribe(audio, language);
        return Map.of("text", text);
    }
}
