package mr.epayment.keycloak;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Second facteur des administrateurs : un code à 6 chiffres (5 minutes, 5 essais) envoyé par WhatsApp (Vonage
 * Messages) ou, pour le développement et les tests automatiques, par email (Mailpit).
 *
 * <p>Canal : attribut utilisateur {@code mfaChannel} ({@code whatsapp} ou {@code email}, modifiable seulement par
 * un administrateur Keycloak), sinon variable {@code MFA_OTP_DEFAULT_CHANNEL}, sinon WhatsApp. Numéro : attribut
 * {@code phoneNumber}, sinon {@code VONAGE_ADMIN_PHONE}.
 */
public final class VonageSmsAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(VonageSmsAuthenticator.class);
    private static final String CODE_HASH = "mfa.otp.code-hash";
    private static final String SALT = "mfa.otp.salt";
    private static final String EXPIRES_AT = "mfa.otp.expires-at";
    private static final String SENT_AT = "mfa.otp.sent-at";
    private static final String ATTEMPTS = "mfa.otp.attempts";
    private static final String CHANNEL = "mfa.otp.channel";
    private static final int VALIDITY_SECONDS = 300;
    /** Un rechargement de la page dans la minute ne renvoie pas de message (coût et spam). */
    private static final int RESEND_DELAY_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final String MESSAGES_URL = "https://api.nexmo.com/v1/messages";
    private static final Pattern SUCCESS = Pattern.compile("\"message_uuid\"\\s*:");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        long now = Instant.now().getEpochSecond();
        if (session.getAuthNote(CODE_HASH) != null && now < parseLong(session.getAuthNote(SENT_AT)) + RESEND_DELAY_SECONDS
                && now < parseLong(session.getAuthNote(EXPIRES_AT))) {
            context.challenge(otpForm(context, session.getAuthNote(CHANNEL), null));
            return;
        }
        String channel = channelFor(context.getUser());
        try {
            String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
            if ("email".equals(channel)) {
                sendEmail(context, code);
            } else {
                String phone = phoneFor(context.getUser());
                if (phone == null) {
                    fail(context, "Aucun numéro WhatsApp MFA n'est configuré pour cet administrateur.");
                    return;
                }
                sendWhatsApp(phone, code);
            }
            String salt = HexFormat.of().formatHex(randomBytes(16));
            session.setAuthNote(SALT, salt);
            session.setAuthNote(CODE_HASH, hash(salt, code));
            session.setAuthNote(SENT_AT, Long.toString(now));
            session.setAuthNote(EXPIRES_AT, Long.toString(now + VALIDITY_SECONDS));
            session.setAuthNote(ATTEMPTS, "0");
            session.setAuthNote(CHANNEL, channel);
            context.challenge(otpForm(context, channel, null));
        } catch (IOException | InterruptedException | EmailException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Envoi du code MFA impossible (canal " + channel + ")", e);
            fail(context, "Impossible d'envoyer le code de connexion. Réessayez ou contactez l'administrateur.");
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        String code = first(context.getHttpRequest().getDecodedFormParameters().get("otp"),
                context.getHttpRequest().getDecodedFormParameters().get("code"));
        String expected = session.getAuthNote(CODE_HASH);
        String salt = session.getAuthNote(SALT);
        int attempts = parseAttempts(session.getAuthNote(ATTEMPTS));
        if (expected == null || salt == null || Instant.now().getEpochSecond() > parseLong(session.getAuthNote(EXPIRES_AT))
                || attempts >= MAX_ATTEMPTS) {
            clear(session);
            fail(context, "Code expiré ou nombre maximal d'essais atteint : reconnectez-vous pour recevoir un nouveau code.");
            return;
        }
        if (code != null && constantTimeEquals(expected, hash(salt, code.trim()))) {
            clear(session);
            context.success();
            return;
        }
        int remaining = MAX_ATTEMPTS - attempts - 1;
        session.setAuthNote(ATTEMPTS, Integer.toString(attempts + 1));
        if (remaining <= 0) {
            clear(session);
            fail(context, "Code incorrect, nombre maximal d'essais atteint : reconnectez-vous pour recevoir un nouveau code.");
            return;
        }
        // Le formulaire revient avec l'erreur : l'administrateur corrige sans recommencer la connexion.
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, otpForm(context,
                session.getAuthNote(CHANNEL), "Code incorrect. Il vous reste " + remaining + " essai"
                        + (remaining > 1 ? "s." : ".")));
    }

    private static jakarta.ws.rs.core.Response otpForm(AuthenticationFlowContext context, String channel,
            String error) {
        var form = context.form()
                .setAttribute("otpLabel", "email".equals(channel) ? "Code reçu par email" : "Code reçu par WhatsApp")
                .setAttribute("otpLogin", Map.of("userOtpCredentials", List.of(), "selectedCredentialId", ""));
        if (error != null) {
            form.setError(error);
        }
        return form.createForm("login-otp.ftl");
    }

    /** Canal de test : l'email passe par le serveur SMTP du realm (Mailpit en développement et en recette). */
    private static void sendEmail(AuthenticationFlowContext context, String code) throws EmailException {
        UserModel user = context.getUser();
        if (user.getEmail() == null) {
            throw new EmailException("Aucun email pour " + user.getUsername());
        }
        String text = "Votre code de connexion E-Payment est : " + code
                + "\n\nIl expire dans 5 minutes. Ne le communiquez à personne.";
        String html = "<p>Votre code de connexion E-Payment est :</p>"
                + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">" + code + "</p>"
                + "<p>Il expire dans 5 minutes. Ne le communiquez à personne.</p>";
        context.getSession().getProvider(EmailSenderProvider.class)
                .send(context.getRealm().getSmtpConfig(), user, "Code de connexion E-Payment", text, html);
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

    /** Attribut {@code mfaChannel} du compte, sinon canal par défaut de l'environnement, sinon WhatsApp. */
    private static String channelFor(UserModel user) {
        String attribute = user == null ? null : user.getFirstAttribute("mfaChannel");
        String channel = attribute != null && !attribute.isBlank() ? attribute
                : requiredUnchecked("MFA_OTP_DEFAULT_CHANNEL");
        return "email".equalsIgnoreCase(channel) ? "email" : "whatsapp";
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

    /** SHA-256 du code avec un sel aléatoire propre à la connexion : le code n'est jamais conservé en clair. */
    private static String hash(String salt, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
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

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseAttempts(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return MAX_ATTEMPTS;
        }
    }

    private static void clear(AuthenticationSessionModel session) {
        for (String note : List.of(CODE_HASH, SALT, EXPIRES_AT, SENT_AT, ATTEMPTS, CHANNEL)) {
            session.removeAuthNote(note);
        }
    }

    private static void fail(AuthenticationFlowContext context, String message) {
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                context.form().setError(message).createErrorPage(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED));
    }

    @Override public boolean requiresUser() { return true; }
    @Override public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
    @Override public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) { }
    @Override public void close() { }
}
