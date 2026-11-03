package mr.epayment.portal.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vérifie qu'un webhook vient bien du fournisseur et qu'il est récent.
 * Signature attendue : {@code v1=hex(HMAC-SHA256(secret, X-Timestamp + "." + corps brut))}.
 */
final class WebhookVerifier {

    private WebhookVerifier() {
    }

    static void verify(String secret, String signature, String timestamp, byte[] body, Duration tolerance,
            Instant now) {
        if (signature == null || timestamp == null) {
            throw new WebhookRejectedException("En-têtes X-Signature ou X-Timestamp absents.");
        }
        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new WebhookRejectedException("X-Timestamp invalide.");
        }
        if (Math.abs(now.getEpochSecond() - sentAt) > tolerance.toSeconds()) {
            // Un webhook ancien ou daté dans le futur est refusé : il pourrait s'agir d'un rejeu.
            throw new WebhookRejectedException("X-Timestamp hors de la fenêtre de " + tolerance.toSeconds() + " s.");
        }
        String expected = "v1=" + hmac(secret, sentAt + "." + new String(body, StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.trim().getBytes(StandardCharsets.US_ASCII))) {
            throw new WebhookRejectedException("Signature invalide.");
        }
    }

    static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Webhook refusé → HTTP 401 (le fournisseur réessaiera ; un attaquant n'apprend rien de plus). */
    static class WebhookRejectedException extends RuntimeException {
        WebhookRejectedException(String message) {
            super(message);
        }
    }
}
