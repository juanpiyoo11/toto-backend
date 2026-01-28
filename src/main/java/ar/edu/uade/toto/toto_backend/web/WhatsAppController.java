package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.service.TwilioService;
import ar.edu.uade.toto.toto_backend.dto.SendMessageRequest;
import ar.edu.uade.toto.toto_backend.dto.SendMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messaging")
public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);
    private final TwilioService twilioService;

    public WhatsAppController(TwilioService twilioService) {
        this.twilioService = twilioService;
    }

    @PostMapping("/send")
    public ResponseEntity<SendMessageResponse> send(@RequestBody SendMessageRequest req) {
        try {
            String messageSid = twilioService.sendSMS(req.to, req.text);
            return ResponseEntity.ok(new SendMessageResponse(messageSid, "ok"));
        } catch (IllegalArgumentException iae) {
            log.warn("Bad SMS request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new SendMessageResponse(null, "bad_request"));
        } catch (IllegalStateException ise) {
            log.error("Twilio misconfigured: {}", ise.getMessage());
            return ResponseEntity.status(500).body(new SendMessageResponse(null, "server_misconfigured"));
        } catch (RuntimeException re) {
            log.error("Twilio SMS send error", re);
            return ResponseEntity.status(502).body(new SendMessageResponse(null, "sms_error"));
        } catch (Exception e) {
            log.error("Unexpected SMS send error", e);
            return ResponseEntity.status(500).body(new SendMessageResponse(null, "error"));
        }
    }

    @GetMapping("/health")
    public String health() { return "ok"; }
}
