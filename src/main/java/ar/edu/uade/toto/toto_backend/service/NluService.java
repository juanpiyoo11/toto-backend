package ar.edu.uade.toto.toto_backend.service;

import ar.edu.uade.toto.toto_backend.dto.NluRouteRequest;
import ar.edu.uade.toto.toto_backend.dto.NluRouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class NluService {

    private static final Logger log = LoggerFactory.getLogger(NluService.class);

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
                            "Reglas de llamada:\n" +
                            "- Órdenes imperativas o en infinitivo (\"llamá\", \"llamar\", \"llamame\", \"quiero que llames …\") → CALL.\n" +
                            "- Pedidos interrogativos de capacidad/permiso (\"¿me podés/podrías/puedes llamar a …?\") → CALL si se menciona contacto explícito.\n" +
                            "- Enunciados descriptivos en 2da persona (\"llamás/llamas a …\") → CALL.\n" +
                            "Regla: si el usuario pide hora o día/fecha actuales, devolvé QUERY_TIME o QUERY_DATE (no ANSWER/UNKNOWN).\n" +
                            "Locale: " + locale + " | TZ: " + tz + " | now_epoch_ms: " + nowMs + "\n" +
                            "Para QUERY_TIME/QUERY_DATE NO generes ack_tts (el cliente habla la respuesta).";

            // Few-shots HORA/FECHA
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

            // Few-shots CALL / SET_ALARM
            ObjectNode ex5U = objectMsg("user", "Llamá a Kevin");
            ObjectNode ex5A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode ex6U = objectMsg("user", "poné una alarma a las 5");
            ObjectNode ex6A = objectMsg("assistant", """
{"intent":"SET_ALARM","confidence":0.97,"needs_confirmation":false,"slots":{"hour":5,"minute":0},"ack_tts":"Listo, programo la alarma.","clarifying_question":null,"safety_notes":null}""");

            // CALL variantes (con/sin acento, pronombres, forma “se llama a …” y portuñol / STT)
            ObjectNode exCall1U = objectMsg("user", "Llama a Kevin");
            ObjectNode exCall1A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall2U = objectMsg("user", "llamalo a Kevin");
            ObjectNode exCall2A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall3U = objectMsg("user", "Se llama a Kevin.");
            ObjectNode exCall3A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            // nuevas variantes (sin "liga")
            ObjectNode exCall4U = objectMsg("user", "Chama a Kevin");
            ObjectNode exCall4A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall5U = objectMsg("user", "Yamalo a Kevin");
            ObjectNode exCall5A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCall6U = objectMsg("user", "Shama a Kevin");
            ObjectNode exCall6A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.96,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");

            // Infinitivo e interrogativas de capacidad/permiso → CALL
            ObjectNode exCall7U = objectMsg("user", "Llamar a Kevin");
            ObjectNode exCall7A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.98,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCallQ1U = objectMsg("user", "¿Me podés llamar a Kevin?");
            ObjectNode exCallQ1A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.97,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Ok, llamo a Kevin.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exCallQ2U = objectMsg("user", "¿Podrías llamar a Kevin?");
            ObjectNode exCallQ2A = objectMsg("assistant", """
{"intent":"CALL","confidence":0.96,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");

            // Enunciado descriptivo en 2da persona → **CALL** (ajuste pedido)
            ObjectNode exCallStmtU = objectMsg("user", "Llamás a Kevin");
            ObjectNode exCallStmtA = objectMsg("assistant", """
{"intent":"CALL","confidence":0.95,"needs_confirmation":false,"slots":{"contact_query":"kevin"},"ack_tts":"Llamando a Kevin.","clarifying_question":null,"safety_notes":null}""");

            // SET_ALARM con “me despertás…”
            ObjectNode exWake1U = objectMsg("user", "¿Me despertás a la una de la tarde?");
            ObjectNode exWake1A = objectMsg("assistant", """
{"intent":"SET_ALARM","confidence":0.97,"needs_confirmation":false,"slots":{"hour":13,"minute":0},"ack_tts":"Listo, te despierto a la una.","clarifying_question":null,"safety_notes":null}""");
            ObjectNode exWake2U = objectMsg("user", "despertame a las 7 y media");
            ObjectNode exWake2A = objectMsg("assistant", """
{"intent":"SET_ALARM","confidence":0.97,"needs_confirmation":false,"slots":{"hour":7,"minute":30},"ack_tts":"Perfecto, alarma a las siete y media.","clarifying_question":null,"safety_notes":null}""");

            // Usuario real
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

            // Schema (SIN OPEN_APP ni app_name)
            ObjectNode schema = mapper.createObjectNode();
            schema.put("$schema", "http://json-schema.org/draft-07/schema#");
            schema.put("type", "object");
            ObjectNode props = schema.putObject("properties");
            props.putObject("intent").put("type","string").putArray("enum")
                    .add("CALL").add("SET_ALARM").add("QUERY_TIME").add("QUERY_DATE")
                    .add("SEND_MESSAGE").add("ANSWER").add("CANCEL").add("UNKNOWN");
            props.putObject("confidence").put("type","number").put("minimum",0.0).put("maximum",1.0);
            props.putObject("needs_confirmation").put("type","boolean");
            ObjectNode slots = props.putObject("slots").put("type","object");
            ObjectNode slotsProps = slots.putObject("properties");
            slotsProps.putObject("contact_query").put("type","string");
            slotsProps.putObject("hour").put("type","integer").put("minimum",0).put("maximum",23);
            slotsProps.putObject("minute").put("type","integer").put("type","integer").put("minimum",0).put("maximum",59);
            slotsProps.putObject("datetime_iso").put("type","string");
            slotsProps.putObject("message_text").put("type","string");
            slots.putArray("required");
            props.putObject("clarifying_question").put("type","string");
            props.putObject("ack_tts").put("type","string");
            props.putObject("safety_notes").put("type","string");
            schema.putArray("required").add("intent").add("confidence").add("needs_confirmation").add("slots");

            ObjectNode jsonSchema = mapper.createObjectNode();
            jsonSchema.put("name", "nlu_route");
            jsonSchema.put("strict", true);
            jsonSchema.set("schema", schema);

            // Payload
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            var input = root.putArray("input");
            input.add(objectMsg("system", systemPrompt));
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
            input.add(objectMsg("user", userText.toString()));
            root.put("temperature", 0.0);

            ObjectNode responseFormat = mapper.createObjectNode();
            responseFormat.put("type","json_schema");
            responseFormat.set("json_schema", jsonSchema);
            root.set("response_format", responseFormat);

            Request reqHttp = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsBytes(root), MediaType.get("application/json")))
                    .build();

            try (Response resp = http.newCall(reqHttp).execute()) {
                String body = (resp.body() != null) ? resp.body().string() : "";
                log.debug("OpenAI HTTP={} body(start)={}", resp.code(), truncate(body, 400));

                // Si falla OpenAI → guardarraíl antes de fallback
                if (!resp.isSuccessful()) {
                    NluRouteResponse guard = guardrailTimeOrDate(norm);
                    if (guard == null) guard = guardrailCall(norm);
                    if (guard != null) return guard;
                    log.warn("NLU/route HTTP {}. text='{}'", resp.code(), text);
                    return fallback("ANSWER", "No estoy seguro, ¿podés repetir?");
                }

                JsonNode r = mapper.readTree(body);
                String json = extractOutputText(r);
                if (json == null || json.isBlank()) {
                    NluRouteResponse guard = guardrailTimeOrDate(norm);
                    if (guard == null) guard = guardrailCall(norm);
                    if (guard != null) return guard;
                    log.warn("NLU/route sin output. text='{}'", text);
                    return fallback("ANSWER", "No te escuché bien. ¿Podés repetir?");
                }

                NluRouteResponse out = mapper.readValue(json.getBytes(StandardCharsets.UTF_8), NluRouteResponse.class);

                // Normalizaciones
                if (out.intent == null) out.intent = "UNKNOWN";
                if (out.slots == null) out.slots = new NluRouteResponse.Slots();

                // Guardarraíl si el modelo dijo ANSWER/UNKNOWN
                String upper = out.intent.toUpperCase(Locale.ROOT);
                if ("ANSWER".equals(upper) || "UNKNOWN".equals(upper)) {
                    NluRouteResponse guard = guardrailTimeOrDate(norm);
                    if (guard == null) guard = guardrailCall(norm);
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

                // Log útil (intención y slots)
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
            NluRouteResponse guard = guardrailTimeOrDate(norm);
            if (guard == null) guard = guardrailCall(norm);
            if (guard != null) return guard;
            return fallback("ANSWER","Perdón, tuve un problema procesando eso.");
        }
    }

    // ===== Guardarraíles normalizados =====
    private static NluRouteResponse guardrailTimeOrDate(String norm) {
        if (norm == null || norm.isBlank()) return null;
        // Hora
        if (containsAny(norm,
                "que hora es", "tenes la hora", "decime la hora", "me decis la hora",
                "tenes hora", "hora es", "la hora es", "hora?","hora")) {
            return quick("QUERY_TIME");
        }
        // Día/Fecha
        if (containsAny(norm,
                "que dia es", "que dia es hoy", "que fecha es", "fecha de hoy",
                "que dia estamos", "me decis la fecha", "decime la fecha")) {
            return quick("QUERY_DATE");
        }
        return null;
    }

    private static NluRouteResponse guardrailCall(String norm) {
        if (norm == null || norm.isBlank()) return null;

        // Disparadores de orden/capacidad/intención (incluye 2da persona)
        boolean looksCallish = containsAny(norm,
                " llama ", " llamame ", " llamalo ", " llamar ", " llamar a ", " se llama ", " llamá ",
                " yama ", " yamar ", " yamalo ",
                " chama ", " chamar ", " chamalo ",
                " shama ", " shamar ", " shamalo ",
                " llamas ", " llamas a ",           // <- 2da persona (con/ sin acento normalizado)
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

    // Extracción mínima de contacto desde texto normalizado (similar a InstructionRouter)
    private static String extractContactForCall(String norm) {
        // patrones con preposición "a"
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
                cand = stripLeadingAIfConsonantForGuard(cand); // "akevin" → "kevin"
                if (!cand.isEmpty()) return cand;
            }
        }

        // sin preposición (ej. "llamalo kevin")
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
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private static String normalizeLite(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}", "");           // quitar diacríticos
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

    // ===== Extractor robusto del Responses API =====
    private String extractOutputText(JsonNode root) {
        // 1) Nuevo Responses API
        JsonNode output = root.path("output");
        if (output.isArray() && output.size() > 0) {
            List<String> parts = new ArrayList<>();
            JsonNode content = output.get(0).path("content");
            if (content.isArray()) {
                for (JsonNode c : content) {
                    String txt = c.path("text").asText(null);
                    if (txt != null && !txt.isBlank()) parts.add(txt);
                    JsonNode j = c.path("json");
                    if (!j.isMissingNode() && !j.isNull()) {
                        try { parts.add(mapper.writeValueAsString(j)); } catch (Exception ignored) {}
                    }
                }
                if (!parts.isEmpty()) return String.join("", parts).trim();
            }
        }
        // 2) output_text (atajo)
        String ot = root.path("output_text").asText(null);
        if (ot != null && !ot.isBlank()) return ot;

        // 3) Compat Chat Completions
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
        var content = msg.putArray("content");
        ObjectNode item = mapper.createObjectNode();
        item.put("type", "text");
        item.put("text", text);
        content.add(item);
        return msg;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @SuppressWarnings("unused")
    private ZoneId safeZone(String tz) {
        try { return ZoneId.of(Objects.requireNonNullElse(tz, defaultTz)); }
        catch (Exception e) { return ZoneId.of(defaultTz); }
    }

    @SuppressWarnings("unused")
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
