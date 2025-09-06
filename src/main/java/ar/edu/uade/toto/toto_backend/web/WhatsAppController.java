package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.service.WhatsAppService;
import ar.edu.uade.toto.toto_backend.dto.SendMessageRequest;
import ar.edu.uade.toto.toto_backend.dto.SendMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);
    private final WhatsAppService wa;

    public WhatsAppController(WhatsAppService wa) {
        this.wa = wa;
    }

    @PostMapping("/send")
    public ResponseEntity<SendMessageResponse> send(@RequestBody SendMessageRequest req) {
        try {
            // SIEMPRE abrimos conversación con template y mandamos el texto en {{1}}
            String idTpl = wa.sendTemplateOpenText(req.to, req.text);
            return ResponseEntity.ok(new SendMessageResponse(idTpl, "ok_template"));
        } catch (WhatsAppService.RecipientNotAllowedException rna) {
            log.warn("Recipient not allowed: {}", rna.getMessage());
            return ResponseEntity.status(409).body(new SendMessageResponse(null, "recipient_not_allowed"));
        } catch (IllegalArgumentException iae) {
            log.warn("Bad WA template request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new SendMessageResponse(null, "bad_request"));
        } catch (IllegalStateException ise) {
            log.error("WA misconfig: {}", ise.getMessage());
            return ResponseEntity.status(500).body(new SendMessageResponse(null, "server_misconfigured"));
        } catch (RuntimeException re) {
            log.error("WhatsApp template send error", re);
            return ResponseEntity.status(502).body(new SendMessageResponse(null, "wa_template_error"));
        } catch (Exception e) {
            log.error("Unexpected WA template send error", e);
            return ResponseEntity.status(500).body(new SendMessageResponse(null, "error"));
        }
    }

    @GetMapping("/health")
    public String health() { return "ok"; }
}
