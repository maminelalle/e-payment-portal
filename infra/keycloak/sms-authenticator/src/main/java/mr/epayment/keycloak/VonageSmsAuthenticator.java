package mr.epayment.keycloak;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class VonageSmsAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(VonageSmsAuthenticator.class);
    private static final String CODE_HASH = "vonage.whatsapp.code-hash";
    private static final String EXPIRES_AT = "vonage.whatsapp.expires-at";
    private static final String ATTEMPTS = "vonage.whatsapp.attempts";
    private static final String MESSAGES_URL = "https://api.nexmo.com/v1/messages";
    private static final Pattern SUCCESS = Pattern.compile("\"message_uuid\"\\s*:");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String phone = phoneFor(context.getUser());
        if (phone == null) {
            fail(context, "Aucun numéro WhatsApp MFA n'est configuré pour cet administrateur.");
            return;
        }
        try {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            sendWhatsApp(phone, code);
            var session = context.getAuthenticationSession();
            session.setAuthNote(CODE_HASH, hash(code));
            session.setAuthNote(EXPIRES_AT, Long.toString(Instant.now().plusSeconds(300).getEpochSecond()));
            session.setAuthNote(ATTEMPTS, "0");
            context.challenge(context.form()
                    .setAttribute("otpLabel", "Code reçu par WhatsApp")
                    .setAttribute("otpLogin", Map.of(
                            "userOtpCredentials", List.of(),
                            "selectedCredentialId", ""))
                    .createForm("login-otp.ftl"));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Vonage WhatsApp request failed", e);
            fail(context, "Impossible d'envoyer le code WhatsApp. Vérifiez la configuration Vonage.");
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        var session = context.getAuthenticationSession();
        String code = first(context.getHttpRequest().getDecodedFormParameters().get("otp"),
                context.getHttpRequest().getDecodedFormParameters().get("code"));
        String expected = session.getAuthNote(CODE_HASH);
        String expires = session.getAuthNote(EXPIRES_AT);
        int attempts = parseAttempts(session.getAuthNote(ATTEMPTS));
        if (expected == null || expires == null || code == null
                || Instant.now().getEpochSecond() > Long.parseLong(expires) || attempts >= 5) {
            clear(session);
            fail(context, "Code OTP invalide, expiré ou nombre maximal d'essais atteint.");
            return;
        }
        session.setAuthNote(ATTEMPTS, Integer.toString(attempts + 1));
        try {
            if (constantTimeEquals(expected, hash(code))) {
                clear(session);
                context.success();
            } else {
                fail(context, "Code OTP incorrect.");
            }
        } catch (IOException e) {
            LOG.error("Unable to validate WhatsApp OTP", e);
            fail(context, "Impossible de vérifier le code OTP.");
        }
    }

    private static void sendWhatsApp(String phone, String code) throws IOException, InterruptedException {
        if ("true".equalsIgnoreCase(System.getenv("VONAGE_WHATSAPP_SANDBOX"))) {
            sendSandboxWhatsApp(phone, code);
            return;
        }
        String template = required("VONAGE_WHATSAPP_TEMPLATE_NAME");
        String languageCode = required("VONAGE_WHATSAPP_TEMPLATE_LANGUAGE");
        String body = "{\"from\":\"" + json(required("VONAGE_WHATSAPP_FROM"))
                + "\",\"to\":\"" + json(phone.replace("+", ""))
                + "\",\"channel\":\"whatsapp\",\"message_type\":\"custom\",\"custom\":"
                + "{\"type\":\"template\",\"template\":{\"name\":\"" + json(template)
                + "\",\"language\":{\"policy\":\"deterministic\",\"code\":\""
                + json(languageCode) + "\"},\"components\":["
                + "{\"type\":\"body\",\"parameters\":[{\"type\":\"text\",\"text\":\""
                + json(code) + "\"}]},"
                + "{\"type\":\"button\",\"sub_type\":\"url\",\"index\":\"0\","
                + "\"parameters\":[{\"type\":\"text\",\"text\":\"" + json(code) + "\"}]}"
                + "]}}}";
        String token = createJwt(required("VONAGE_WHATSAPP_APPLICATION_ID"),
                required("VONAGE_WHATSAPP_PRIVATE_KEY_PATH"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(MESSAGES_URL))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || !SUCCESS.matcher(response.body()).find()) {
            throw new IOException("Vonage WhatsApp rejected request: HTTP " + response.statusCode()
                    + ", response=" + safeResponse(response.body()));
        }
    }

    private static void sendSandboxWhatsApp(String phone, String code) throws IOException, InterruptedException {
        String credentials = required("VONAGE_API_KEY") + ":" + required("VONAGE_API_SECRET");
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.US_ASCII));
        String body = "{\"from\":\"" + json(required("VONAGE_WHATSAPP_FROM"))
                + "\",\"to\":\"" + json(phone.replace("+", ""))
                + "\",\"message_type\":\"text\",\"text\":\"Votre code E-Payment est "
                + json(code) + "\",\"channel\":\"whatsapp\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(required("VONAGE_MESSAGES_URL")))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || !response.body().contains("message_uuid")) {
            throw new IOException("Vonage WhatsApp sandbox rejected request: HTTP "
                    + response.statusCode() + ", response=" + safeResponse(response.body()));
        }
    }

    private static String createJwt(String applicationId, String keyPath) throws IOException {
        try {
            String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            long now = Instant.now().getEpochSecond();
            String payload = base64Url("{\"application_id\":\"" + json(applicationId)
                    + "\",\"iat\":" + now + ",\"exp\":" + (now + 300)
                    + ",\"jti\":\"" + UUID.randomUUID() + "\"}");
            String signingInput = header + "." + payload;
            var signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(readPrivateKey(Path.of(keyPath)));
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Unable to create Vonage JWT", e);
        }
    }

    private static PrivateKey readPrivateKey(Path path) throws IOException, java.security.GeneralSecurityException {
        String pem = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private String phoneFor(UserModel user) {
        String configured = requiredUnchecked("VONAGE_ADMIN_PHONE");
        String userPhone = user == null ? null : user.getFirstAttribute("phoneNumber");
        String phone = userPhone == null || userPhone.isBlank() ? configured : userPhone;
        return phone != null && phone.replace("+", "").matches("222\\d{8}") ? phone : null;
    }

    private static String required(String name) throws IOException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IOException(name + " is not configured");
        }
        return value;
    }

    private static String requiredUnchecked(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String hash(String value) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(required("VONAGE_API_SECRET").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Unable to hash OTP", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),
                b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeResponse(String value) {
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private static String first(List<String> first, List<String> second) {
        if (first != null && !first.isEmpty()) return first.get(0);
        return second == null || second.isEmpty() ? null : second.get(0);
    }

    private static int parseAttempts(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    private static void clear(org.keycloak.sessions.AuthenticationSessionModel session) {
        session.removeAuthNote(CODE_HASH);
        session.removeAuthNote(EXPIRES_AT);
        session.removeAuthNote(ATTEMPTS);
    }

    private static void fail(AuthenticationFlowContext context, String message) {
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setError(message).createErrorPage(null));
    }

    @Override public boolean requiresUser() { return true; }
    @Override public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
    @Override public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) { }
    @Override public void close() { }
}
