package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.model.ConversationMessage;
import ar.edu.uade.toto.toto_backend.service.ConversationMemoryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
    private final ConversationMemoryService conversationMemory;

    public OpenAIController(OpenAISTTService stt, OpenAIPromptService prompts, ConversationMemoryService conversationMemory) {
        this.stt = stt;
        this.prompts = prompts;
        this.conversationMemory = conversationMemory;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest body) {
        String prompt = (body != null && body.prompt != null) ? body.prompt : "";
        String userId = (body != null && body.userId != null && !body.userId.isBlank()) 
                        ? body.userId 
                        : "default-user";
        
        try {
            List<ConversationMessage> history = conversationMemory.getHistory(userId);
            
            String reply = prompts.ask(prompt, history);
            
            conversationMemory.addMessage(userId, "user", prompt);
            conversationMemory.addMessage(userId, "assistant", reply);
            
            String sessionId = conversationMemory.getCurrentSessionId(userId);
            
            return new AskResponse(reply, sessionId);
        } catch (Exception e) {
            return new AskResponse("ERROR: " + e.getMessage(), null);
        }
    }

    @PostMapping("/conversation/clear")
    public Map<String, String> clearConversation(@RequestBody Map<String, String> body) {
        String userId = body.getOrDefault("userId", "default-user");
        conversationMemory.clearSession(userId);
        return Map.of("status", "ok", "message", "Conversación limpiada para usuario: " + userId);
    }

    @PostMapping("/conversation/stats")
    public Map<String, Object> getConversationStats() {
        return conversationMemory.getStats();
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
