package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.NluRouteRequest;
import ar.edu.uade.toto.toto_backend.dto.NluRouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

@Service
public class NluService {

    private static final Logger log = LoggerFactory.getLogger(NluService.class);

    // --- Flags de estrategia ---
    private static final boolean USE_GUARDRAILS_AFTER_MODEL = false;   // el modelo manda
    private static final boolean USE_GUARDRAILS_ON_FAILURE = true;     // solo fallback si falla API

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String defaultLocale;
    private final String systemStyle;   // compat
    private final String defaultTz;

    public NluService(
            @Value("${openai.api-key:}") String apiKeyProp,
            @Value("${openai.api-url:https://api.openai.com/v1/responses}") String apiUrl,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.default-locale:es-AR}") String defaultLocale,
            @Value("${openai.system-style:}") String systemStyle,
            @Value("${openai.default-tz:America/Argentina/Buenos_Aires}") String defaultTz
    ) {
        String env = System.getenv("OPENAI_API_KEY");
        this.apiKey = (apiKeyProp != null && !apiKeyProp.isBlank()) ? apiKeyProp : (env != null ? env : "");
        this.apiUrl = apiUrl;
        this.model = model;
        this.defaultLocale = (defaultLocale == null || defaultLocale.isBlank()) ? "es-AR" : defaultLocale;
        this.systemStyle = (systemStyle == null) ? "" : systemStyle;
        this.defaultTz = (defaultTz == null || defaultTz.isBlank()) ? "America/Argentina/Buenos_Aires" : defaultTz;
    }

    public NluRouteResponse route(NluRouteRequest req) {
        try {
            ensureApiKey();

            String text   = (req != null && req.text != null) ? req.text : "";
            String locale = (req != null && req.locale != null && !req.locale.isBlank()) ? req.locale : defaultLocale;
            String tz     = (req != null && req.tz != null && !req.tz.isBlank()) ? req.tz : defaultTz;
            long nowMs    = (req != null && req.now_epoch_ms != null) ? req.now_epoch_ms : System.currentTimeMillis();

            // ===== Normalización ligera =====
            String norm = normalizeLite(text);

            // ===== Prompt (clasificador) =====
            String systemPrompt =
                    "Sos el router NLU de \"Toto\". Devolvés SOLO JSON válido según el schema (sin texto extra).\n" +
                            "Evitá falsos positivos. Si hay duda real: needs_confirmation=true y clarifying_question breve.\n" +
                            "Elegí exactamente una intención entre: " +
                            "CALL, SET_ALARM, QUERY_TIME, QUERY_DATE, SEND_MESSAGE, " +
                            "SPOTIFY_PLAY, SPOTIFY_PAUSE, SPOTIFY_RESUME, SPOTIFY_NEXT, SPOTIFY_PREV, " +
                            "SPOTIFY_SET_VOLUME, SPOTIFY_SET_SHUFFLE, SPOTIFY_SET_REPEAT, " +
                            "FALL, " +
                            "ANSWER, CANCEL, UNKNOWN.\n" +
                            "\n" +
                            "Reglas de llamada:\n" +
                            "- Imperativos/infinitivo (\"llamá\", \"llamar\", \"llamame\", \"quiero que llames …\") → CALL.\n" +
                            "- Preguntas de capacidad/permiso (\"¿me podés/podrías/puedes llamar a …?\") → CALL si hay contacto explícito.\n" +
                            "- Enunciados descriptivos en 2da persona (\"llamás/llamas a …\") → CALL.\n" +
                            "\n" +
                            "Reglas de mensaje:\n" +
                            "- \"mandale/escribile/escribirle/decile/decirle/avisale/enviá/enviar/enviale/enviarle\" + \"a <persona>\" + (\"que\" | \":\") + <texto> → SEND_MESSAGE.\n" +
                            "- Si falta destinatario o texto, needs_confirmation=true y clarifying_question adecuada.\n" +
                            "\n" +
                            "Hora/fecha ACTUAL:\n" +
                            "- Usá QUERY_TIME/QUERY_DATE solo si piden explícitamente hora/fecha actual (\"¿qué hora es?\", \"¿qué día es hoy?\").\n" +
                            "- NO uses QUERY_TIME para \"¿a qué hora ...?\", horarios, agenda o recomendaciones → eso es ANSWER.\n" +
                            "\n" +
                            "Spotify (reproducción de música):\n" +
                            "- \"poné/reproducí/tocá\" + <tema|artista|playlist|álbum> → SPOTIFY_PLAY. Guardá la consigna en slots.message_text.\n" +
                            "- \"poné música\" sin detalle → SPOTIFY_PLAY con needs_confirmation=true y clarifying_question=\"¿Qué querés escuchar?\".\n" +
                            "- \"poné pausa\"/\"pausá\"/\"pará la música\" → SPOTIFY_PAUSE.\n" +
                            "- \"seguí\"/\"reanudar\"/\"continuá la música\" → SPOTIFY_RESUME.\n" +
                            "- \"siguiente\"/\"pasá el tema\" → SPOTIFY_NEXT. | \"anterior\" → SPOTIFY_PREV.\n" +
                            "- Volumen: \"volumen al 40%\" → SPOTIFY_SET_VOLUME con slots.message_text=\"40\"; " +
                            "\"subí el volumen\" → \"up\"; \"bajá el volumen\" → \"down\".\n" +
                            "- Shuffle: \"activá/sacá el aleatorio\" → SPOTIFY_SET_SHUFFLE con slots.message_text=\"on\"/\"off\".\n" +
                            "- Repeat: \"repetir tema\" → SPOTIFY_SET_REPEAT con slots.message_text=\"track\"; \"repetir lista\" → \"context\"; \"sacar repeat\" → \"off\".\n" +
                            "\n" +
                            "FALL:\n" +
                            "- Detección semántica de caída/lesión: \"me caí\", \"me pegué\", \"me duele la cadera\", \"no me puedo levantar\" → FALL.\n" +
                            "- Evitar condicional/hipotéticos: \"si me caigo\", \"cuando me caiga\" → NO FALL.\n" +
                            "\n" +
                            "Locale: " + locale + " | TZ: " + tz + " | now_epoch_ms: " + nowMs + "\n" +
                            "Para QUERY_TIME/QUERY_DATE NO generes ack_tts (el cliente habla la respuesta).";

            // ===== Few-shots base =====
            ObjectNode ex1U = objectMsg("user", "¿Qué hora es?");
            ObjectNode ex1A = objectMsg("assistant", """
{"intent":"QUERY_TIME","confidence":0.99,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");
            ObjectNode ex2U = objectMsg("user", "Que hora es");
            ObjectNode ex2A = objectMsg("assistant", """
{"intent":"QUERY_TIME","confidence":0.99,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");
            ObjectNode ex3U = objectMsg("user", "¿Qué día es hoy?");
            ObjectNode ex3A = objectMsg("assistant", """
{"intent":"QUERY_DATE","confidence":0.99,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");
            ObjectNode ex4U = objectMsg("user", "me decís la fecha?");
            ObjectNode ex4A = objectMsg("assistant", """
{"intent":"QUERY_DATE","confidence":0.99,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");

            ObjectNode ex5U = objectMsg("user", "Llamá a Kevin");
            ObjectNode ex5A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode ex6U = objectMsg("user", "poné una alarma a las 5");
            ObjectNode ex6A = objectMsg("assistant", """
{"intent":"SET_ALARM","confidence":0.97,"needs_confirmation":false,"slots":{"hour":5,"minute":0},"ack_tts":"Listo, programo la alarma.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exCall1U = objectMsg("user", "llamalo a Kevin");
            ObjectNode exCall1A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exMsg1U = objectMsg("user", "Mandale a Kevin que llego en 10");
            ObjectNode exMsg1A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.98,"needs_confirmation":false,
 "slots":{"contact_query":"kevin","message_text":"llego en 10"},
 "ack_tts":"Listo, se lo mando a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exMsg2U = objectMsg("user", "Mandale mensaje a Sofi");
            ObjectNode exMsg2A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.95,"needs_confirmation":true,
 "slots":{"contact_query":"sofi","message_text":null},
 "ack_tts":null,"clarifying_question":"¿Qué querés que le diga a Sofi?","safety_notes":null}""");
            ObjectNode exMsg3U = objectMsg("user", "Decirle a Flor que la amo mucho");
            ObjectNode exMsg3A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.98,"needs_confirmation":false,
 "slots":{"contact_query":"flor","message_text":"la amo mucho"},
 "ack_tts":"Listo, lo mando a Flor.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exNegTimeSem1U = objectMsg("user", "Decime la hora en la que me recomendás cepillarme los dientes.");
            ObjectNode exNegTimeSem1A = objectMsg("assistant", """
{"intent":"ANSWER","confidence":0.95,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");

            // ===== Few-shots Spotify =====
            ObjectNode sp1U = objectMsg("user", "Poné Soda Stereo");
            ObjectNode sp1A = objectMsg("assistant", """
{"intent":"SPOTIFY_PLAY","confidence":0.97,"needs_confirmation":false,
 "slots":{"message_text":"soda stereo"},
 "ack_tts":"Reproduciendo Soda Stereo en Spotify.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp2U = objectMsg("user", "Reproducí De música ligera");
            ObjectNode sp2A = objectMsg("assistant", """
{"intent":"SPOTIFY_PLAY","confidence":0.97,"needs_confirmation":false,
 "slots":{"message_text":"de musica ligera"},
 "ack_tts":"Voy con De música ligera.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp3U = objectMsg("user", "Poné música");
            ObjectNode sp3A = objectMsg("assistant", """
{"intent":"SPOTIFY_PLAY","confidence":0.90,"needs_confirmation":true,
 "slots":{"message_text":null},
 "ack_tts":null,"clarifying_question":"¿Qué querés escuchar?","safety_notes":null}""");
            ObjectNode sp4U = objectMsg("user", "Poné pausa");
            ObjectNode sp4A = objectMsg("assistant", """
{"intent":"SPOTIFY_PAUSE","confidence":0.99,"needs_confirmation":false,
 "slots":{},"ack_tts":"Pauso la música.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp5U = objectMsg("user", "Seguí la música");
            ObjectNode sp5A = objectMsg("assistant", """
{"intent":"SPOTIFY_RESUME","confidence":0.99,"needs_confirmation":false,
 "slots":{},"ack_tts":"Sigo reproduciendo.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp6U = objectMsg("user", "Pasá al siguiente");
            ObjectNode sp6A = objectMsg("assistant", """
{"intent":"SPOTIFY_NEXT","confidence":0.99,"needs_confirmation":false,
 "slots":{},"ack_tts":"Siguiente tema.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp7U = objectMsg("user", "Volvé al anterior");
            ObjectNode sp7A = objectMsg("assistant", """
{"intent":"SPOTIFY_PREV","confidence":0.99,"needs_confirmation":false,
 "slots":{},"ack_tts":"Tema anterior.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp8U = objectMsg("user", "Volumen al 40 por ciento");
            ObjectNode sp8A = objectMsg("assistant", """
{"intent":"SPOTIFY_SET_VOLUME","confidence":0.98,"needs_confirmation":false,
 "slots":{"message_text":"40"},
 "ack_tts":"Volumen en 40%.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp9U = objectMsg("user", "Subí el volumen");
            ObjectNode sp9A = objectMsg("assistant", """
{"intent":"SPOTIFY_SET_VOLUME","confidence":0.95,"needs_confirmation":false,
 "slots":{"message_text":"up"},
 "ack_tts":"Subo el volumen.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp10U = objectMsg("user", "Activá el modo aleatorio");
            ObjectNode sp10A = objectMsg("assistant", """
{"intent":"SPOTIFY_SET_SHUFFLE","confidence":0.97,"needs_confirmation":false,
 "slots":{"message_text":"on"},
 "ack_tts":"Activo aleatorio.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp11U = objectMsg("user", "Sacá el aleatorio");
            ObjectNode sp11A = objectMsg("assistant", """
{"intent":"SPOTIFY_SET_SHUFFLE","confidence":0.97,"needs_confirmation":false,
 "slots":{"message_text":"off"},
 "ack_tts":"Desactivo aleatorio.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp12U = objectMsg("user", "Repetir tema");
            ObjectNode sp12A = objectMsg("assistant", """
{"intent":"SPOTIFY_SET_REPEAT","confidence":0.97,"needs_confirmation":false,
 "slots":{"message_text":"track"},
 "ack_tts":"Repito el tema.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode sp13U = objectMsg("user", "Sacar repeat");
            ObjectNode sp13A = objectMsg("assistant", """
{"intent":"SPOTIFY_SET_REPEAT","confidence":0.97,"needs_confirmation":false,
 "slots":{"message_text":"off"},
 "ack_tts":"Desactivo repetir.","clarifying_question":null,"safety_notes":null}""");

            // ===== Few-shots FALL =====
            ObjectNode fall1U = objectMsg("user", "me caí");
            ObjectNode fall1A = objectMsg("assistant", """
{"intent":"FALL","confidence":0.99,"needs_confirmation":false,
 "slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":"possible fall"}""");
            ObjectNode fall2U = objectMsg("user", "me pegué fuerte y me duele la cadera");
            ObjectNode fall2A = objectMsg("assistant", """
{"intent":"FALL","confidence":0.97,"needs_confirmation":false,
 "slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":"possible fall"}""");
            ObjectNode fall3U = objectMsg("user", "me tropecé y me caí al piso");
            ObjectNode fall3A = objectMsg("assistant", """
{"intent":"FALL","confidence":0.97,"needs_confirmation":false,
 "slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":"possible fall"}""");

            // ===== Usuario real =====
            StringBuilder userText = new StringBuilder();
            userText.append("texto: ").append(safe(text)).append("\n");
            userText.append("texto_normalizado: ").append(norm).append("\n");
            userText.append("tz: ").append(tz).append("\n");
            userText.append("now_epoch_ms: ").append(nowMs).append("\n");
            if (req != null && req.context != null && !req.context.isEmpty()) {
                userText.append("contexto: ").append(mapSafe(req.context)).append("\n");
            }
            if (req != null && req.hints != null && !req.hints.isEmpty()) {
                userText.append("hints: ").append(mapSafe(req.hints)).append("\n");
            }

            // ===== JSON Schema =====
            ObjectNode schema = mapper.createObjectNode();
            schema.put("$schema", "http://json-schema.org/draft-07/schema#");
            schema.put("type", "object");
            schema.put("additionalProperties", false);

            ObjectNode props = schema.putObject("properties");
            ArrayNode intents = props.putObject("intent").put("type","string").putArray("enum");
            intents.add("CALL").add("SET_ALARM").add("QUERY_TIME").add("QUERY_DATE")
                    .add("SEND_MESSAGE")
                    .add("SPOTIFY_PLAY").add("SPOTIFY_PAUSE").add("SPOTIFY_RESUME")
                    .add("SPOTIFY_NEXT").add("SPOTIFY_PREV")
                    .add("SPOTIFY_SET_VOLUME").add("SPOTIFY_SET_SHUFFLE").add("SPOTIFY_SET_REPEAT")
                    .add("FALL")                 // <-- NUEVO
                    .add("ANSWER").add("CANCEL").add("UNKNOWN");

            props.putObject("confidence").put("type","number").put("minimum",0.0).put("maximum",1.0);
            props.putObject("needs_confirmation").put("type","boolean");

            // slots
            ObjectNode slots = props.putObject("slots");
            slots.put("type","object");
            slots.put("additionalProperties", false);
            ObjectNode slotsProps = slots.putObject("properties");

            ArrayNode tContact = slotsProps.putObject("contact_query").putArray("type");
            tContact.add("string").add("null");

            ObjectNode hourNode = slotsProps.putObject("hour");
            ArrayNode tHour = hourNode.putArray("type"); tHour.add("integer").add("null");
            hourNode.put("minimum", 0).put("maximum", 23);

            ObjectNode minuteNode = slotsProps.putObject("minute");
            ArrayNode tMinute = minuteNode.putArray("type"); tMinute.add("integer").add("null");
            minuteNode.put("minimum", 0).put("maximum", 59);

            ArrayNode tDt = slotsProps.putObject("datetime_iso").putArray("type");
            tDt.add("string").add("null");

            ArrayNode tMsg = slotsProps.putObject("message_text").putArray("type");
            tMsg.add("string").add("null");

            ArrayNode slotsReq = slots.putArray("required");
            slotsReq.add("contact_query");
            slotsReq.add("hour");
            slotsReq.add("minute");
            slotsReq.add("datetime_iso");
            slotsReq.add("message_text");

            ArrayNode tClar = props.putObject("clarifying_question").putArray("type");
            tClar.add("string").add("null");
            ArrayNode tAck = props.putObject("ack_tts").putArray("type");
            tAck.add("string").add("null");
            ArrayNode tSafe = props.putObject("safety_notes").putArray("type");
            tSafe.add("string").add("null");

            ArrayNode rootReq = schema.putArray("required");
            rootReq.add("intent");
            rootReq.add("confidence");
            rootReq.add("needs_confirmation");
            rootReq.add("slots");
            rootReq.add("clarifying_question");
            rootReq.add("ack_tts");
            rootReq.add("safety_notes");

            // ===== Payload Responses API =====
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            root.put("temperature", 0.0);
            root.put("stream", true);
            root.put("max_tokens", 80);

            ArrayNode input = root.putArray("input");
            input.add(objectMsg("system", systemPrompt));

            // shots
            input.add(ex1U); input.add(ex1A);
            input.add(ex2U); input.add(ex2A);
            input.add(ex3U); input.add(ex3A);
            input.add(ex4U); input.add(ex4A);
            input.add(ex5U); input.add(ex5A);
            input.add(ex6U); input.add(ex6A);
            input.add(exCall1U); input.add(exCall1A);
            input.add(exMsg1U); input.add(exMsg1A);
            input.add(exMsg2U); input.add(exMsg2A);
            input.add(exMsg3U); input.add(exMsg3A);
            input.add(exNegTimeSem1U); input.add(exNegTimeSem1A);

            // Spotify shots
            input.add(sp1U); input.add(sp1A);
            input.add(sp2U); input.add(sp2A);
            input.add(sp3U); input.add(sp3A);
            input.add(sp4U); input.add(sp4A);
            input.add(sp5U); input.add(sp5A);
            input.add(sp6U); input.add(sp6A);
            input.add(sp7U); input.add(sp7A);
            input.add(sp8U); input.add(sp8A);
            input.add(sp9U); input.add(sp9A);
            input.add(sp10U); input.add(sp10A);
            input.add(sp11U); input.add(sp11A);
            input.add(sp12U); input.add(sp12A);
            input.add(sp13U); input.add(sp13A);

            // FALL shots
            input.add(fall1U); input.add(fall1A);
            input.add(fall2U); input.add(fall2A);
            input.add(fall3U); input.add(fall3A);

            // usuario real al final
            input.add(objectMsg("user", userText.toString()));

            // text.format json_schema
            ObjectNode textObj = mapper.createObjectNode();
            ObjectNode textFormat = mapper.createObjectNode();
            textFormat.put("type", "json_schema");
            textFormat.put("name", "nlu_route");
            textFormat.put("strict", true);
            textFormat.set("schema", schema);
            textObj.set("format", textFormat);
            root.set("text", textObj);

            Request reqHttp = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsBytes(root), MediaType.get("application/json")))
                    .build();

            try (Response resp = http.newCall(reqHttp).execute()) {
                String body = (resp.body() != null) ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    log.warn("NLU/route HTTP {}. errBody(start)={}", resp.code(), truncate(body, 400));
                    if (USE_GUARDRAILS_ON_FAILURE) {
                        NluRouteResponse guard = guardrailTimeOrDate(norm);
                        if (guard == null) guard = guardrailCall(norm);
                        if (guard == null) guard = guardrailSendMessage(norm);
                        if (guard == null) guard = guardrailFall(norm);  // <-- NUEVO
                        if (guard != null) return guard;
                    }
                    return fallback("ANSWER", "No estoy seguro, ¿podés repetir?");
                } else {
                    log.debug("OpenAI HTTP={} body(start)={}", resp.code(), truncate(body, 400));
                }

                String json = extractOutputText(mapper.readTree(body));
                if (json == null || json.isBlank()) {
                    if (USE_GUARDRAILS_ON_FAILURE) {
                        NluRouteResponse guard = guardrailTimeOrDate(norm);
                        if (guard == null) guard = guardrailCall(norm);
                        if (guard == null) guard = guardrailSendMessage(norm);
                        if (guard == null) guard = guardrailFall(norm);  // <-- NUEVO
                        if (guard != null) return guard;
                    }
                    log.warn("NLU/route sin output. text='{}'", text);
                    return fallback("ANSWER", "No te escuché bien. ¿Podés repetir?");
                }

                NluRouteResponse out = mapper.readValue(json.getBytes(StandardCharsets.UTF_8), NluRouteResponse.class);

                // Normalizaciones
                if (out.intent == null) out.intent = "UNKNOWN";
                if (out.slots == null) out.slots = new NluRouteResponse.Slots();

                // ===== Post-model: SEND_MESSAGE robusto =====
                if ("SEND_MESSAGE".equalsIgnoreCase(out.intent)) {
                    MsgParts mp = extractMsgParts(norm);
                    if (mp != null) {
                        if ((out.slots.contact_query == null || out.slots.contact_query.isBlank()) && mp.who != null)
                            out.slots.contact_query = mp.who;
                        if ((out.slots.message_text == null || out.slots.message_text.isBlank()) && mp.text != null)
                            out.slots.message_text = mp.text;
                    }

                    boolean hasWho  = out.slots.contact_query != null && !out.slots.contact_query.isBlank();
                    boolean hasText = out.slots.message_text != null && !out.slots.message_text.isBlank();

                    if (hasWho && hasText) {
                        out.needs_confirmation = false;
                        if (out.ack_tts == null || out.ack_tts.isBlank())
                            out.ack_tts = "Listo, lo mando.";
                        if (out.confidence < 0.95) out.confidence = 0.95;
                        out.clarifying_question = null;
                    } else {
                        out.needs_confirmation = true;
                        out.ack_tts = null;
                        out.clarifying_question = hasWho
                                ? ("¿Qué querés que le diga a " + out.slots.contact_query + "?")
                                : "¿A quién querés mandarle el mensaje?";
                        if (out.confidence > 0.9) out.confidence = 0.9;
                    }
                }

                // ===== Post-model: FALL =====
                if ("FALL".equalsIgnoreCase(out.intent)) {
                    out.needs_confirmation = false;
                    out.ack_tts = null; // el cliente dispara el flujo de caídas
                    if (out.confidence < 0.90) out.confidence = 0.90;
                }

                // Guardrails post-model deshabilitados por defecto
                if (USE_GUARDRAILS_AFTER_MODEL &&
                        ("ANSWER".equalsIgnoreCase(out.intent) || "UNKNOWN".equalsIgnoreCase(out.intent))) {
                    NluRouteResponse guard = guardrailTimeOrDate(norm);
                    if (guard == null) guard = guardrailCall(norm);
                    if (guard == null) guard = guardrailSendMessage(norm);
                    if (guard == null) guard = guardrailFall(norm);
                    if (guard != null) out = guard;
                }

                // Para hora/fecha, habla el cliente
                if ("QUERY_TIME".equals(out.intent) || "QUERY_DATE".equals(out.intent)) {
                    out.ack_tts = null;
                    out.needs_confirmation = false;
                    if (out.confidence < 0.95) out.confidence = 0.95;
                }

                // Bounds
                if (out.confidence < 0.0) out.confidence = 0.0;
                if (out.confidence > 1.0) out.confidence = 1.0;

                String slotsLog = String.format(
                        "{contact='%s', hour=%s, minute=%s, dt='%s', msg='%s'}",
                        safe(out.slots.contact_query),
                        out.slots.hour, out.slots.minute,
                        safe(out.slots.datetime_iso), safe(out.slots.message_text)
                );
                log.info("NLU/route OK intent={} conf={} needsConf={} norm='{}' slots={}",
                        out.intent, out.confidence, out.needs_confirmation, norm, slotsLog);

                if ("SET_ALARM".equalsIgnoreCase(out.intent)) {
                    boolean faltaHora = (out.slots.hour == null || out.slots.minute == null);
                    Integer minsRel = parseRelativeMinutes(norm); // detecta "en 10 minutos", "en 2 horas", etc.

                    if (faltaHora && minsRel != null && minsRel > 0) {
                        java.time.ZoneId zone = safeZone(tz);
                        java.time.ZonedDateTime tgt = java.time.Instant.ofEpochMilli(nowMs)
                                .atZone(zone)
                                .plusMinutes(minsRel)
                                .withSecond(0).withNano(0);

                        out.slots.hour = tgt.getHour();
                        out.slots.minute = tgt.getMinute();
                        out.slots.datetime_iso = tgt.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        out.needs_confirmation = false;
                        if (out.confidence < 0.95) out.confidence = 0.95;
                        if (out.ack_tts == null || out.ack_tts.isBlank()) {
                            out.ack_tts = minsRel == 1 ? "Listo, en 1 minuto." : ("Listo, en " + minsRel + " minutos.");
                        }
                    }
                }

                return out;
            }
        } catch (Exception e) {
            log.error("NLU/route error", e);
            String norm = normalizeLite(req != null ? req.text : null);
            if (USE_GUARDRAILS_ON_FAILURE) {
                NluRouteResponse guard = guardrailTimeOrDate(norm);
                if (guard == null) guard = guardrailCall(norm);
                if (guard == null) guard = guardrailSendMessage(norm);
                if (guard == null) guard = guardrailFall(norm);
                if (guard != null) return guard;
            }
            return fallback("ANSWER","Perdón, tuve un problema procesando eso.");
        }
    }

    // ===== Guardarraíles (solo fallback) =====
    private static NluRouteResponse guardrailTimeOrDate(String norm) {
        if (norm == null || norm.isBlank()) return null;
        boolean asksTimeNow =
                norm.matches(".*\\b(que hora es|tenes la hora|tienes la hora|decime la hora( ahora)?|dime la hora( ahora)?|me decis la hora|me dices la hora|hora actual)\\b.*");
        boolean asksDateNow =
                norm.matches(".*\\b(que dia es( hoy)?|que fecha es( hoy)?|fecha de hoy|que dia estamos|me decis la fecha|me dices la fecha|decime la fecha|dime la fecha)\\b.*");
        if (asksTimeNow)  return quick("QUERY_TIME");
        if (asksDateNow)  return quick("QUERY_DATE");
        return null;
    }

    private static NluRouteResponse guardrailCall(String norm) {
        if (norm == null || norm.isBlank()) return null;
        boolean looksCallish = containsAny(norm,
                " llama ", " llamame ", " llamalo ", " llamar ", " llamar a ", " se llama ", " llamá ",
                " yama ", " yamar ", " yamalo ", " chama ", " chamar ", " chamalo ",
                " shama ", " shamar ", " shamalo ", " llamas ", " llamas a ",
                " quiero que llames ", " quiero llamar ",
                " me podes llamar ", " me podrias llamar ", " me puedes llamar ",
                " podes llamar ", " podrias llamar ", " puedes llamar ", " podria llamar ");
        if (looksCallish) {
            String cq = extractContactForCall(norm);
            NluRouteResponse r = new NluRouteResponse();
            r.intent = "CALL";
            r.confidence = 0.97;
            r.needs_confirmation = false;
            r.ack_tts = "Ok, llamo.";
            r.slots = new NluRouteResponse.Slots();
            if (cq != null && !cq.isBlank()) r.slots.contact_query = cq;
            return r;
        }
        return null;
    }

    private NluRouteResponse guardrailSendMessage(String norm) {
        if (norm == null || norm.isBlank()) return null;

        boolean looksMsg = looksMsgSpanish(norm);
        if (!looksMsg) return null;

        MsgParts m = extractMsgParts(norm);

        NluRouteResponse r = new NluRouteResponse();
        r.intent = "SEND_MESSAGE";
        r.slots  = new NluRouteResponse.Slots();
        r.confidence = 0.96;
        if (m != null && m.who != null && !m.who.isBlank()) r.slots.contact_query = m.who;
        if (m != null && m.text != null && !m.text.isBlank()) r.slots.message_text = m.text;

        if (r.slots.contact_query == null || r.slots.contact_query.isBlank()) {
            r.needs_confirmation = true;
            r.clarifying_question = "¿A quién querés mandarle el mensaje?";
            r.ack_tts = null;
        } else if (r.slots.message_text == null || r.slots.message_text.isBlank()) {
            r.needs_confirmation = true;
            r.clarifying_question = "¿Qué querés que le diga a " + r.slots.contact_query + "?";
            r.ack_tts = null;
        } else {
            r.needs_confirmation = false;
            r.ack_tts = "Listo, lo mando.";
        }
        return r;
    }

    private static NluRouteResponse guardrailFall(String norm) {
        if (norm == null || norm.isBlank()) return null;

        boolean saidFall = norm.matches(".*\\b(me cai|me ca[ií]do|me tropec[eé]|me desmaye|me pegue|me golpee)\\b.*");
        boolean pain     = norm.matches(".*\\b(me duele|me lastime|me fracture|me rompi|me torci|no puedo levantarme)\\b.*");
        boolean hypothetical = norm.matches(".*\\b(si|cuando)\\s+me\\s+(caigo|caiga|llego a caer)\\b.*");

        if (!hypothetical && (saidFall || pain)) {
            NluRouteResponse r = new NluRouteResponse();
            r.intent = "FALL";
            r.confidence = 0.96;
            r.needs_confirmation = false;
            r.ack_tts = null;
            r.safety_notes = "possible fall";
            r.slots = new NluRouteResponse.Slots();
            return r;
        }
        return null;
    }

    private static final class MsgParts { final String who, text; MsgParts(String w, String t){who=w;text=t;} }

    private MsgParts extractMsgParts(String norm) {
        // norm ya viene sin acentos y en minúscula
        String[] pats = new String[] {
                "\\b(?:mandale|manda|mandar|escribile|escribirle|escribe|escribir|decile|decirle|dile|avisale|avisa|avisar|envia|enviar|enviale|enviarle)(?:\\s+un\\s+mensaje)?\\s+a\\s+([a-z0-9\\s.-]{1,40})\\s*(?:que|de que|:|–|-)?\\s*(.+)$",
                "\\b(?:mensaje|msj)\\s+a\\s+([a-z0-9\\s.-]{1,40})\\s*(?:que|:)?\\s*(.+)$"
        };
        for (String p : pats) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(norm);
            if (m.find()) {
                String who  = cleanPersonGuard(m.group(1));
                String text = cleanMessageGuard(m.groupCount() >= 2 ? m.group(2) : "");
                if (!who.isEmpty() && !text.isEmpty()) return new MsgParts(who, text);
            }
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("\\b(?:mandale|manda|mandar|escribile|escribirle|escribe|escribir|decile|decirle|dile|avisale|avisa|avisar|envia|enviar|enviale|enviarle)(?:\\s+un\\s+mensaje)?\\s+a\\s+([a-z0-9\\s.-]{1,40})\\b")
                .matcher(norm);
        if (m2.find()) {
            String who = cleanPersonGuard(m2.group(1));
            if (!who.isEmpty()) return new MsgParts(who, "");
        }
        return null;
    }

    private static String cleanPersonGuard(String s) {
        if (s == null) return "";
        s = s.replaceAll("(?:\\s+por\\s+favor.*$)|(?:\\s+gracias.*$)|(?:\\s+ahora.*$)|(?:\\s+urgente.*$)|(?:\\s+ya.*$)", " ");
        s = s.replaceAll("[^a-z0-9\\s.-]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.startsWith("a") && s.length() >= 2 && "bcdfghjklmnñpqrstvwxyz".indexOf(s.charAt(1)) >= 0) s = s.substring(1);
        String[] tok = s.split("\\s+");
        int limit = Math.min(tok.length, 4);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < limit; i++) { if (i>0) out.append(' '); out.append(tok[i]); }
        return out.toString();
    }

    private static String cleanMessageGuard(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("(\\s+por\\s+favor.*$)|(\\s+gracias.*$)", "").trim();
        return s;
    }

    // ===== Helpers CALL =====
    private static String extractContactForCall(String norm) {
        String[] pats = new String[] {
                "\\bllam\\w*\\s+a\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\bse\\s+llama(?:\\s+a)?\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\b(?:me\\s+)?pod(?:es|rias|ria|emos|rian|emos)\\s+llamar\\s+a\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\b(?:podes|podrias|puedes|podria)\\s+llamar\\s+a\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\bquiero\\s+llamar\\s+a\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\byam\\w*\\s+a\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\bcham\\w*\\s+a\\s+([a-z0-9\\s-]{1,40})\\b",
                "\\bsham\\w*\\s+a\\s+([a-z0-9\\s-]{1,40})\\b"
        };
        for (String p : pats) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(norm);
            if (m.find()) {
                String cand = cleanContactCandidateForGuard(m.group(1));
                cand = stripLeadingAIfConsonantForGuard(cand);
                if (!cand.isEmpty()) return cand;
            }
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("\\b(?:llam\\w*|yam\\w*|cham\\w*|sham\\w*)\\s+([a-z0-9][a-z0-9\\s-]{1,40})\\b")
                .matcher(norm);
        if (m2.find()) {
            String first = m2.group(1).trim().split("\\s+")[0];
            String cand = cleanContactCandidateForGuard(first);
            cand = stripLeadingAIfConsonantForGuard(cand);
            if (!cand.isEmpty()) return cand;
        }
        return null;
    }

    private static String cleanContactCandidateForGuard(String s) {
        if (s == null) return "";
        String out = s.replaceAll(" por favor.*$", " ")
                .replaceAll(" gracias.*$", " ")
                .replaceAll(" ahora.*$", " ")
                .replaceAll(" urgente.*$", " ")
                .replaceAll(" ya.*$", " ");
        out = out.replaceAll("[^a-z0-9\\s-]", " ");
        out = out.replaceAll("\\s+", " ").trim();
        String[] tok = out.split("\\s+");
        int limit = Math.min(tok.length, 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) { if (i>0) sb.append(' '); sb.append(tok[i]); }
        return sb.toString();
    }

    private static String stripLeadingAIfConsonantForGuard(String s) {
        if (s == null || s.length() < 2) return s == null ? "" : s;
        if (s.charAt(0) == 'a') {
            char c = s.charAt(1);
            if ("bcdfghjklmnñpqrstvwxyz".indexOf(c) >= 0) return s.substring(1);
        }
        return s;
    }

    // ===== Helpers =====
    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    /** Detector robusto de intención de MENSAJE (palabras con límites). */
    private static boolean looksMsgSpanish(String norm) {
        if (norm == null || norm.isBlank()) return false;
        String re = ".*\\b(?:mandale|manda|mandar|escribile|escribirle|escribe|escribir|decile|decirle|dile|avisale|avisa|avisar|envia|enviar|enviale|enviarle|mensaje\\s+a|msj\\s+a)\\b.*";
        return norm.matches(re);
    }

    private static String normalizeLite(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT);
        t = t.replaceAll("[¿?¡!.,;:()\\[\\]\"']", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private static NluRouteResponse quick(String intent) {
        NluRouteResponse r = new NluRouteResponse();
        r.intent = intent;
        r.confidence = 0.99;
        r.needs_confirmation = false;
        r.ack_tts = null;
        r.slots = new NluRouteResponse.Slots();
        return r;
    }

    private static Integer parseRelativeMinutes(String norm) {
        if (norm == null || norm.isBlank()) return null;

        // en X minutos
        java.util.regex.Matcher mMin = java.util.regex.Pattern
                .compile("\\ben\\s+(\\d{1,3})\\s+minut(?:o|os)\\b")
                .matcher(norm);
        if (mMin.find()) {
            try { return Math.max(1, Integer.parseInt(mMin.group(1))); } catch (Exception ignore) {}
        }

        // en X horas
        java.util.regex.Matcher mHr = java.util.regex.Pattern
                .compile("\\ben\\s+(\\d{1,2})\\s+hor(?:a|as)\\b")
                .matcher(norm);
        if (mHr.find()) {
            try { return Math.max(1, Integer.parseInt(mHr.group(1)) * 60); } catch (Exception ignore) {}
        }

        // variantes simples comunes
        if (norm.contains("media hora")) return 30;
        if (norm.contains("un minuto") || norm.contains("1 minuto")) return 1;
        if (norm.contains("una hora") || norm.contains("1 hora")) return 60;

        return null;
    }


    // ===== Extractor del Responses API =====
    private String extractOutputText(JsonNode root) {
        JsonNode parsed = root.path("output_parsed");
        if (!parsed.isMissingNode() && !parsed.isNull()) {
            try { return mapper.writeValueAsString(parsed); } catch (Exception ignored) {}
        }
        JsonNode output = root.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode content = output.get(0).path("content");
            if (content.isArray() && content.size() > 0) {
                String t = content.get(0).path("text").asText(null);
                if (t != null && !t.isBlank()) return t;
            }
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            String cc = choices.get(0).path("message").path("content").asText(null);
            if (cc != null && !cc.isBlank()) return cc;
        }
        return null;
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("Falta OPENAI_API_KEY (o property openai.api-key)");
    }

    private String safe(String s) { return (s == null) ? "" : s; }

    private String mapSafe(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    private ObjectNode objectMsg(String role, String text) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", text);
        return msg;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private ZoneId safeZone(String tz) {
        try { return ZoneId.of(Objects.requireNonNullElse(tz, defaultTz)); }
        catch (Exception e) { return ZoneId.of(defaultTz); }
    }

    private Locale toLocale(String s) {
        if (s == null || s.isBlank()) return new Locale("es","AR");
        String[] p = s.split("[-_]");
        if (p.length >= 2) return new Locale(p[0], p[1]);
        return new Locale(s);
    }

    private NluRouteResponse fallback(String intent, String ack) {
        NluRouteResponse r = new NluRouteResponse();
        r.intent = intent;
        r.confidence = 0.0;
        r.needs_confirmation = true;
        r.ack_tts = ack;
        r.slots = new NluRouteResponse.Slots();
        return r;
    }
}
