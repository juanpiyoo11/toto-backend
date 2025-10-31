package ar.edu.uade.toto.toto_backend.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import ar.edu.uade.toto.toto_backend.dto.AskRequest;
import ar.edu.uade.toto.toto_backend.dto.AskResponse;
import ar.edu.uade.toto.toto_backend.service.OpenAIPromptService;
import ar.edu.uade.toto.toto_backend.service.OpenAISTTService;

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
            String reply = prompts.ask(prompt);
            return new AskResponse(reply);
        } catch (Exception e) {
            return new AskResponse("ERROR: " + e.getMessage());
        }
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
