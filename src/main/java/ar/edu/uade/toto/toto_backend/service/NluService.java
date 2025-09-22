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
    // El modelo manda: NO aplicar guardrails post-modelo
    private static final boolean USE_GUARDRAILS_AFTER_MODEL = false;
    // Usar guardrails SOLO como fallback si la API falla o viene sin output
    private static final boolean USE_GUARDRAILS_ON_FAILURE = true;

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

            // ===== Normalización ligera para guardarraíles =====
            String norm = normalizeLite(text);

            // ===== Prompt (clasificador puro) =====
            String systemPrompt =
                    "Sos el router NLU de \"Toto\". Devolvés SOLO JSON válido según el schema (sin texto extra).\n" +
                            "Evitá falsos positivos. Si hay duda real: needs_confirmation=true y clarifying_question breve.\n" +
                            "Elegí exactamente una intención: CALL, SET_ALARM, QUERY_TIME, QUERY_DATE, SEND_MESSAGE, ANSWER, CANCEL, UNKNOWN.\n" +
                            "\n" +
                            "Reglas de llamada:\n" +
                            "- Órdenes imperativas o en infinitivo (\"llamá\", \"llamar\", \"llamame\", \"quiero que llames …\") → CALL.\n" +
                            "- Preguntas de capacidad/permiso (\"¿me podés/podrías/puedes llamar a …?\") → CALL si hay contacto explícito.\n" +
                            "- Enunciados descriptivos en 2da persona (\"llamás/llamas a …\") → CALL.\n" +
                            "\n" +
                            "Reglas de mensaje:\n" +
                            "- \"mandale/escribile/decile/avisale\" + \"a <persona>\" + (\"que\" | \":\") + <texto> → SEND_MESSAGE con contact_query y message_text.\n" +
                            "- Si falta destinatario o texto, needs_confirmation=true y clarifying_question adecuada.\n" +
                            "\n" +
                            "Hora/fecha ACTUAL (MUY IMPORTANTE):\n" +
                            "- Usá QUERY_TIME/QUERY_DATE **solo** si el usuario pide explícitamente la hora/fecha **actual** (p. ej. \"¿qué hora es?\", \"decime la hora ahora\", \"¿qué día es hoy?\").\n" +
                            "- **NO** uses QUERY_TIME para preguntas del tipo \"¿a qué hora …?\", \"la hora en la que …\", horarios de apertura, agenda o recomendaciones. Esas van como ANSWER (u otra intención si corresponde).\n" +
                            "\n" +
                            "Locale: " + locale + " | TZ: " + tz + " | now_epoch_ms: " + nowMs + "\n" +
                            "Para QUERY_TIME/QUERY_DATE NO generes ack_tts (el cliente habla la respuesta).";

            // ===== Few-shots =====
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

            ObjectNode exCall1U = objectMsg("user", "Llama a Kevin");
            ObjectNode exCall1A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall2U = objectMsg("user", "llamalo a Kevin");
            ObjectNode exCall2A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall3U = objectMsg("user", "Se llama a Kevin.");
            ObjectNode exCall3A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall4U = objectMsg("user", "Chama a Kevin");
            ObjectNode exCall4A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall5U = objectMsg("user", "Yamalo a Kevin");
            ObjectNode exCall5A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall6U = objectMsg("user", "Shama a Kevin");
            ObjectNode exCall6A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.96,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall7U = objectMsg("user", "Llamar a Kevin");
            ObjectNode exCall7A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCallQ1U = objectMsg("user", "¿Me podés llamar a Kevin?");
            ObjectNode exCallQ1A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCallQ2U = objectMsg("user", "¿Podrías llamar a Kevin?");
            ObjectNode exCallQ2A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.96,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCallStmtU = objectMsg("user", "Llamás a Kevin");
            ObjectNode exCallStmtA = objectMsg("assistant", """
{"intent":"CALL","confidence":0.95,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exWake1U = objectMsg("user", "¿Me despertás a la una de la tarde?");
            ObjectNode exWake1A = objectMsg("assistant", """
{"intent":"SET_ALARM","confidence":0.97,"needs_confirmation":false,"slots":{"hour":13,"minute":0},"ack_tts":"Listo, te despierto a la una.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exWake2U = objectMsg("user", "despertame a las 7 y media");
            ObjectNode exWake2A = objectMsg("assistant", """
{"intent":"SET_ALARM","confidence":0.97,"needs_confirmation":false,"slots":{"hour":7,"minute":30},"ack_tts":"Perfecto, alarma a las siete y media.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exMsg1U = objectMsg("user", "Mandale a Kevin que llego en 10");
            ObjectNode exMsg1A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.98,"needs_confirmation":false,
 "slots":{"contact_query":"kevin","message_text":"llego en 10"},
 "ack_tts":"Listo, se lo mando a Kevin.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exMsg2U = objectMsg("user", "Escribile a mamá: estoy saliendo");
            ObjectNode exMsg2A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.98,"needs_confirmation":false,
 "slots":{"contact_query":"mama","message_text":"estoy saliendo"},
 "ack_tts":"Ok, le escribo a mamá.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exMsg3U = objectMsg("user", "Avisale a Lucas que voy a llegar 20 tarde");
            ObjectNode exMsg3A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.97,"needs_confirmation":false,
 "slots":{"contact_query":"lucas","message_text":"voy a llegar 20 tarde"},
 "ack_tts":"Hecho, le aviso a Lucas.","clarifying_question":null,"safety_notes":null}""");

            ObjectNode exMsg4U = objectMsg("user", "Mandale mensaje a Sofi");
            ObjectNode exMsg4A = objectMsg("assistant", """
{"intent":"SEND_MESSAGE","confidence":0.95,"needs_confirmation":true,
 "slots":{"contact_query":"sofi","message_text":null},
 "ack_tts":null,"clarifying_question":"¿Qué querés que le diga a Sofi?","safety_notes":null}""");

            // ===== Few-shots negativos semánticos para evitar QUERY_TIME por “a qué hora …” =====
            ObjectNode exNegTimeSem1U = objectMsg("user", "Decime la hora en la que me recomendás cepillarme los dientes.");
            ObjectNode exNegTimeSem1A = objectMsg("assistant", """
{"intent":"ANSWER","confidence":0.95,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");
            ObjectNode exNegTimeSem2U = objectMsg("user", "¿A qué hora abre el banco Galicia?");
            ObjectNode exNegTimeSem2A = objectMsg("assistant", """
{"intent":"ANSWER","confidence":0.95,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");
            ObjectNode exNegTimeSem3U = objectMsg("user", "¿A qué hora me conviene cenar si entreno a las 20?");
            ObjectNode exNegTimeSem3A = objectMsg("assistant", """
{"intent":"ANSWER","confidence":0.95,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}""");

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
            props.putObject("intent").put("type","string").putArray("enum")
                    .add("CALL").add("SET_ALARM").add("QUERY_TIME").add("QUERY_DATE")
                    .add("SEND_MESSAGE").add("ANSWER").add("CANCEL").add("UNKNOWN");
            props.putObject("confidence").put("type","number").put("minimum",0.0).put("maximum",1.0);
            props.putObject("needs_confirmation").put("type","boolean");

            // slots
            ObjectNode slots = props.putObject("slots");
            slots.put("type","object");
            slots.put("additionalProperties", false);
            ObjectNode slotsProps = slots.putObject("properties");

            // tipos unión para permitir null
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

            // required de slots = TODAS sus keys
            ArrayNode slotsReq = slots.putArray("required");
            slotsReq.add("contact_query");
            slotsReq.add("hour");
            slotsReq.add("minute");
            slotsReq.add("datetime_iso");
            slotsReq.add("message_text");

            // opcionales raíz (también unión con null)
            ArrayNode tClar = props.putObject("clarifying_question").putArray("type");
            tClar.add("string").add("null");
            ArrayNode tAck = props.putObject("ack_tts").putArray("type");
            tAck.add("string").add("null");
            ArrayNode tSafe = props.putObject("safety_notes").putArray("type");
            tSafe.add("string").add("null");

            // required raíz = TODAS las keys declaradas en properties
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
            input.add(exCall2U); input.add(exCall2A);
            input.add(exCall3U); input.add(exCall3A);
            input.add(exCall4U); input.add(exCall4A);
            input.add(exCall5U); input.add(exCall5A);
            input.add(exCall6U); input.add(exCall6A);
            input.add(exCall7U); input.add(exCall7A);
            input.add(exCallQ1U); input.add(exCallQ1A);
            input.add(exCallQ2U); input.add(exCallQ2A);
            input.add(exCallStmtU); input.add(exCallStmtA);
            input.add(exWake1U); input.add(exWake1A);
            input.add(exWake2U); input.add(exWake2A);
            input.add(exMsg1U); input.add(exMsg1A);
            input.add(exMsg2U); input.add(exMsg2A);
            input.add(exMsg3U); input.add(exMsg3A);
            input.add(exMsg4U); input.add(exMsg4A);

            // negativos anti-falsos
            input.add(exNeg1U()); input.add(exNeg1A());
            input.add(exNeg2U()); input.add(exNeg2A());

            // negativos semánticos de "a qué hora ..."
            input.add(exNegTimeSem1U); input.add(exNegTimeSem1A);
            input.add(exNegTimeSem2U); input.add(exNegTimeSem2A);
            input.add(exNegTimeSem3U); input.add(exNegTimeSem3A);

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
                        if (guard != null) return guard;
                    }
                    return fallback("ANSWER", "No estoy seguro, ¿podés repetir?");
                } else {
                    log.debug("OpenAI HTTP={} body(start)={}", resp.code(), truncate(body, 400));
                }

                // Parse: prioriza output_parsed
                String json = extractOutputText(mapper.readTree(body));
                if (json == null || json.isBlank()) {
                    if (USE_GUARDRAILS_ON_FAILURE) {
                        NluRouteResponse guard = guardrailTimeOrDate(norm);
                        if (guard == null) guard = guardrailCall(norm);
                        if (guard == null) guard = guardrailSendMessage(norm);
                        if (guard != null) return guard;
                    }
                    log.warn("NLU/route sin output. text='{}'", text);
                    return fallback("ANSWER", "No te escuché bien. ¿Podés repetir?");
                }

                NluRouteResponse out = mapper.readValue(json.getBytes(StandardCharsets.UTF_8), NluRouteResponse.class);

                // Normalizaciones
                if (out.intent == null) out.intent = "UNKNOWN";
                if (out.slots == null) out.slots = new NluRouteResponse.Slots();

                // ===== Post-model minimal: SEND_MESSAGE sanity =====
                if ("SEND_MESSAGE".equalsIgnoreCase(out.intent)) {
                    // Solo aceptamos si el texto realmente contiene patrón de mensaje
                    MsgParts mp = extractMsgParts(norm);
                    boolean looksMsg = containsAny(norm,
                            " mandale ", " manda ", " mandar ",
                            " escribile ", " escribe ", " escribir ",
                            " decile ", " dile ",
                            " avisale ", " avisa ", " avisar ",
                            " mensaje a ", " msj a ", " mandale un mensaje a ", " mandale mensaje a ");

                    // Completar slots con lo extraído localmente (si faltan)
                    if (mp != null) {
                        if ((out.slots.contact_query == null || out.slots.contact_query.isBlank()) && mp.who != null)
                            out.slots.contact_query = mp.who;
                        if ((out.slots.message_text == null || out.slots.message_text.isBlank()) && mp.text != null)
                            out.slots.message_text = mp.text;
                    }

                    // Si no hay patrón claro o faltan partes importantes → pedir confirmación
                    if (!looksMsg || mp == null || out.slots.contact_query == null || out.slots.contact_query.isBlank()
                            || out.slots.message_text == null || out.slots.message_text.isBlank()) {
                        out.needs_confirmation = true;
                        out.clarifying_question = (out.slots.contact_query == null || out.slots.contact_query.isBlank())
                                ? "¿A quién querés mandarle el mensaje?"
                                : ("¿Qué querés que le diga a " + out.slots.contact_query + "?");
                        out.ack_tts = null; // no anunciar envío
                        // Reforzar confianza moderada
                        if (out.confidence > 0.9) out.confidence = 0.9;
                    }
                }

                // (Opcional) Guardrails post-modelo: deshabilitados por defecto
                if (USE_GUARDRAILS_AFTER_MODEL &&
                        ("ANSWER".equalsIgnoreCase(out.intent) || "UNKNOWN".equalsIgnoreCase(out.intent))) {
                    NluRouteResponse guard = guardrailTimeOrDate(norm);
                    if (guard == null) guard = guardrailCall(norm);
                    if (guard == null) guard = guardrailSendMessage(norm);
                    if (guard != null) out = guard;
                }

                // Para hora/fecha, garantizamos que el cliente hable
                if ("QUERY_TIME".equals(out.intent) || "QUERY_DATE".equals(out.intent)) {
                    out.ack_tts = null;
                    out.needs_confirmation = false;
                    if (out.confidence < 0.95) out.confidence = 0.95;
                }

                // Bounds de confianza
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

                return out;
            }
        } catch (Exception e) {
            log.error("NLU/route error", e);
            String norm = normalizeLite(req != null ? req.text : null);
            if (USE_GUARDRAILS_ON_FAILURE) {
                NluRouteResponse guard = guardrailTimeOrDate(norm);
                if (guard == null) guard = guardrailCall(norm);
                if (guard == null) guard = guardrailSendMessage(norm);
                if (guard != null) return guard;
            }
            return fallback("ANSWER","Perdón, tuve un problema procesando eso.");
        }
    }

    // ===== Guardarraíles (solo fallback) =====
    private static NluRouteResponse guardrailTimeOrDate(String norm) {
        if (norm == null || norm.isBlank()) return null;

        // match explícito para HORA ACTUAL
        boolean asksTimeNow =
                norm.matches(".*\\b(que hora es|tenes la hora|tienes la hora|decime la hora( ahora)?|dime la hora( ahora)?|me decis la hora|me dices la hora|hora actual)\\b.*");

        // match explícito para FECHA ACTUAL
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
        boolean looksMsg = containsAny(norm,
                " mandale ", " manda ", " mandar ",
                " escribile ", " escribe ", " escribir ",
                " decile ", " dile ",
                " avisale ", " avisa ", " avisar ",
                " mensaje a ", " msj a ", " mandale un mensaje a ", " mandale mensaje a ");
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

    private static final class MsgParts { final String who, text; MsgParts(String w, String t){who=w;text=t;} }

    private MsgParts extractMsgParts(String norm) {
        String[] pats = new String[] {
                "\\b(?:mandale|manda|mandar|escribile|escribe|escribir|decile|dile|avisale|avisa|avisar)(?:\\s+un\\s+mensaje)?\\s+a\\s+([a-z0-9\\s.-]{1,40})\\s*(?:que|de que|:|–|-)?\\s*(.+)$",
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
                .compile("\\b(?:mandale|manda|escribile|escribe|decile|dile|avisale|avisa|avisar)(?:\\s+un\\s+mensaje)?\\s+a\\s+([a-z0-9\\s.-]{1,40})\\b")
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

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
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

    // ===== Extractor del Responses API =====
    private String extractOutputText(JsonNode root) {
        // 1) cuando el server ya parseó/validó
        JsonNode parsed = root.path("output_parsed");
        if (!parsed.isMissingNode() && !parsed.isNull()) {
            try { return mapper.writeValueAsString(parsed); } catch (Exception ignored) {}
        }
        // 2) respuesta en output[].content[].text
        JsonNode output = root.path("output");
        if (output.isArray() && output.size() > 0) {
            JsonNode content = output.get(0).path("content");
            if (content.isArray() && content.size() > 0) {
                String t = content.get(0).path("text").asText(null);
                if (t != null && !t.isBlank()) return t;
            }
        }
        // 3) compat Chat Completions
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

    // Mensaje simple (como tu OpenAIPromptService)
    private ObjectNode objectMsg(String role, String text) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", text);
        return msg;
    }

    // Atajos para negativos simples ya usados antes
    private ObjectNode exNeg1U() { return objectMsg("user", "Como estas?"); }
    private ObjectNode exNeg1A() { return objectMsg("assistant", """
{"intent":"ANSWER","confidence":0.80,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}"""); }
    private ObjectNode exNeg2U() { return objectMsg("user", "No, no, nada. No te llamé recién."); }
    private ObjectNode exNeg2A() { return objectMsg("assistant", """
{"intent":"ANSWER","confidence":0.85,"needs_confirmation":false,"slots":{},"ack_tts":null,"clarifying_question":null,"safety_notes":null}"""); }

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
