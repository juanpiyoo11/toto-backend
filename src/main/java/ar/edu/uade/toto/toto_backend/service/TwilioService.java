package ar.edu.uade.toto.toto_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);
    private static final MediaType FORM_URLENCODED = MediaType.parse("application/x-www-form-urlencoded");
    private static final MediaType JSON = MediaType.parse("application/json");

    private final String accountSid;
    private final String authToken;
    private final String fromPhoneNumber;
    private final String defaultRegion;
    private final boolean drop9ForAr;
    private final String templateSid;
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public static class RecipientNotAllowedException extends Exception {
        public RecipientNotAllowedException(String message) { super(message); }
    }

    public TwilioService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.from-phone-number:}") String fromPhoneNumber,
            @Value("${twilio.default-region:AR}") String defaultRegion,
            @Value("${twilio.argentina.drop9:true}") boolean drop9ForAr,
            @Value("${twilio.template-sid:}") String templateSid
    ) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromPhoneNumber = fromPhoneNumber;
        this.defaultRegion = (defaultRegion == null || defaultRegion.isBlank()) ? "AR" : defaultRegion.trim().toUpperCase();
        this.drop9ForAr = drop9ForAr;
        this.templateSid = (templateSid == null || templateSid.isBlank()) ? "" : templateSid.trim();

        if (this.accountSid == null || this.accountSid.isBlank() ||
            this.authToken == null || this.authToken.isBlank() ||
            this.fromPhoneNumber == null || this.fromPhoneNumber.isBlank()) {
            log.warn("⚠️ Twilio credentials not configured. SMS sending disabled.");
        } else {
            log.info("✅ TwilioService initialized with fromPhoneNumber={}", this.fromPhoneNumber);
            Twilio.init(this.accountSid, this.authToken);
        }
        log.info("☎️ Normalizer: defaultRegion={} | argentina.drop9={}", this.defaultRegion, this.drop9ForAr);
        log.info("🧩 Template SID: {}", this.templateSid);
    }

    public String sendSMS(String toRaw, String text) throws Exception {
        if (accountSid == null || accountSid.isBlank() ||
            authToken == null || authToken.isBlank() ||
            fromPhoneNumber == null || fromPhoneNumber.isBlank()) {
            throw new IllegalStateException("Twilio credentials not configured in environment variables.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be empty.");
        }

        String to = normalizeForTwilio(toRaw);
        if (to.isBlank()) {
            throw new IllegalArgumentException("Invalid destination number: " + safe(toRaw));
        }

        try {
            if (templateSid != null && !templateSid.isBlank()) {
                return sendTemplateMessage(to, text);
            } else {
                return sendPlainMessage(to, text);
            }
        } catch (Exception e) {
            log.error("❌ Twilio SMS API error: {}", e.getMessage(), e);
            throw new RuntimeException("Twilio SMS error: " + e.getMessage(), e);
        }
    }

    private String sendPlainMessage(String to, String text) throws Exception {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        RequestBody body = new FormBody.Builder()
                .add("From", fromPhoneNumber)
                .add("To", to)
                .add("Body", text)
                .build();

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(accountSid, authToken))
                .post(body)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("❌ Twilio API {}: {}", resp.code(), respBody);
                throw new RuntimeException("Twilio API error " + resp.code() + ": " + respBody);
            }

            JsonNode root = mapper.readTree(respBody);
            String sid = root.path("sid").asText("");
            log.info("✅ SMS sent to {} (sid={})", mask(to), sid);
            return sid;
        }
    }

    private String sendTemplateMessage(String to, String text) throws Exception {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

        RequestBody body = new FormBody.Builder()
                .add("From", fromPhoneNumber)
                .add("To", to)
                .add("ContentSid", templateSid)
                .add("ContentVariables", createContentVariables(text))
                .build();

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(accountSid, authToken))
                .post(body)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                log.warn("❌ Twilio TEMPLATE API {}: {}", resp.code(), respBody);
                throw new RuntimeException("Twilio TEMPLATE error " + resp.code() + ": " + respBody);
            }

            JsonNode root = mapper.readTree(respBody);
            String sid = root.path("sid").asText("");
            log.info("✅ SMS template sent to {} (sid={})", mask(to), sid);
            return sid;
        }
    }

    private String createContentVariables(String text) throws Exception {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("1", text);
        return mapper.writeValueAsString(vars);
    }

    private String normalizeForTwilio(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            Phonenumber.PhoneNumber proto = util.parse(s, defaultRegion);
            if (!util.isValidNumber(proto)) throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "invalid");

            String e164 = util.format(proto, PhoneNumberFormat.E164);
            if (drop9ForAr && e164.startsWith("+549")) {
                e164 = "+54" + e164.substring(4);
            }
            return e164;
        } catch (NumberParseException e) {
            String digits = s.replaceAll("[^0-9]", "");
            if ("AR".equalsIgnoreCase(defaultRegion)) {
                if (drop9ForAr && digits.startsWith("549")) digits = "54" + digits.substring(3);
                if (!digits.startsWith("54") && (digits.length() == 10 || digits.length() == 11)) {
                    digits = "54" + digits.replaceFirst("^0+", "");
                }
            }
            return "+" + digits;
        }
    }

    private static String mask(String n) {
        if (n == null || n.isEmpty()) return "****";
        int keep = Math.min(4, n.length());
        return "****" + n.substring(n.length() - keep);
    }

    private static String safe(String s) { return (s == null) ? "" : s; }
}

