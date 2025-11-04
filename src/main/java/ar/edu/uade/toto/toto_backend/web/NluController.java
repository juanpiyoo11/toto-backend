package ar.edu.uade.toto.toto_backend.web;

import ar.edu.uade.toto.toto_backend.dto.NluRouteRequest;
import ar.edu.uade.toto.toto_backend.dto.NluRouteResponse;
import ar.edu.uade.toto.toto_backend.service.NluService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nlu")
public class NluController {

    private final NluService nlu;

    public NluController(NluService nlu) {
        this.nlu = nlu;
    }

    @PostMapping("/route")
    public NluRouteResponse route(@RequestBody NluRouteRequest body) {
        try {
            if (body == null || body.text == null || body.text.isBlank()) {
                NluRouteResponse r = new NluRouteResponse();
                r.intent = "UNKNOWN";
                r.confidence = 0.0;
                r.needs_confirmation = true;
                r.clarifying_question = "No te escuché bien. ¿Podés repetir?";
                return r;
            }
            return nlu.route(body);
        } catch (Exception e) {
            NluRouteResponse r = new NluRouteResponse();
            r.intent = "ANSWER";
            r.confidence = 0.0;
            r.needs_confirmation = true;
            r.ack_tts = "Perdón, tuve un problema procesando eso.";
            return r;
        }
    }
}
