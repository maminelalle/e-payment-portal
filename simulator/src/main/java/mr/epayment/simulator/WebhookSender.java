package mr.epayment.simulator;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mr.epayment.simulator.SimPayment.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notifications (webhooks) envoyées au portail, au format de chaque fournisseur.
 * <p>
 * Signature : {@code X-Signature: v1=hex(HMAC-SHA256(secret, X-Timestamp + "." + corps))}, avec
 * {@code X-Event-Id} unique (anti-rejeu côté portail). Réessais après 10 s, 30 s, 60 s si le portail
 * ne répond pas 2xx. Le même événement (même identifiant) est renvoyé à chaque réessai.
 */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    private final RestClient http;
    private final JsonMapper json;
    private final TaskScheduler scheduler;
    private final SimulatorProperties properties;

    WebhookSender(RestClient.Builder builder, JsonMapper json, TaskScheduler scheduler,
            SimulatorProperties properties) {
        this.http = builder.build();
        this.json = json;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    public void send(SimPayment payment) {
        Event event = buildEvent(payment);
        deliver(payment.provider(), event, 0);
    }

    /** Signature d'un corps, exposée pour les tests et la documentation. */
    public static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deliver(ProviderCode provider, Event event, int attempt) {
        var config = properties.provider(provider);
        long timestamp = Instant.now().getEpochSecond();
        try {
            http.post().uri(config.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Provider", provider.name())
                    .header("X-Event-Id", event.id())
                    .header("X-Timestamp", Long.toString(timestamp))
                    .header("X-Signature", sign(config.webhookSecret(), timestamp, event.body()))
                    .body(event.body())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook {} {} livré au portail", provider, event.id());
        } catch (RuntimeException e) {
            List<Duration> retries = properties.webhookRetryDelays();
            if (attempt < retries.size()) {
                Duration delay = retries.get(attempt);
                log.warn("Webhook {} {} refusé ({}), nouvel essai dans {} s", provider, event.id(), e.getMessage(),
                        delay.toSeconds());
                scheduler.schedule(() -> deliver(provider, event, attempt + 1), Instant.now().plus(delay));
            } else {
                log.error("Webhook {} {} abandonné après {} essais", provider, event.id(), attempt + 1);
            }
        }
    }

    private Event buildEvent(SimPayment p) {
        Map<String, Object> body = new LinkedHashMap<>();
        String id;
        switch (p.provider()) {
            case BANKILY -> {
                id = "evt_" + UUID.randomUUID().toString().replace("-", "");
                body.put("eventId", id);
                body.put("type", p.state() == State.SUCCEEDED ? "payment.succeeded" : "payment.failed");
                body.put("paymentId", p.id());
                body.put("merchantReference", p.merchantReference());
                body.put("status", BankilyController.status(p));
                body.put("amount", p.amount());
                body.put("currency", p.currency());
                body.put("failureCode", p.failureCode());
                body.put("occurredAt", p.updatedAt().toString());
            }
            case MASRVI -> {
                // Identifiant unique même après un redémarrage (un compteur repartirait à zéro : faux rejeu).
                id = "ntf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                body.put("evenementId", id);
                body.put("idTransaction", p.id());
                body.put("reference", p.merchantReference());
                body.put("etat", MasrviController.etat(p));
                body.put("code", MasrviController.code(p));
                body.put("montant", p.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
                body.put("dateHeure", p.updatedAt().toString());
            }
            case VISA -> {
                id = "evt_v_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                body.put("id", id);
                body.put("type", switch (p.state()) {
                    case SUCCEEDED -> "checkout.session.completed";
                    case EXPIRED -> "checkout.session.expired";
                    default -> "checkout.session.declined";
                });
                body.put("sessionId", p.id());
                body.put("orderReference", p.merchantReference());
                body.put("status", VisaController.status(p));
                body.put("amount", p.amount());
                body.put("currency", p.currency());
                body.put("card", p.maskedPan() == null ? null
                        : Map.of("brand", "VISA", "maskedPan", p.maskedPan(), "token", p.cardToken()));
                body.put("declineReason", VisaController.declineReason(p));
                body.put("created", p.updatedAt().getEpochSecond());
            }
            default -> throw new IllegalStateException();
        }
        return new Event(id, json.writeValueAsString(body));
    }

    private record Event(String id, String body) {
    }
}
