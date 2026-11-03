package mr.epayment.portal.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import mr.epayment.portal.gateway.PaymentProvider.Outcome;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduction des webhooks de chaque fournisseur vers {@link ProviderWebhook} (docs/03-api-fournisseurs-simules.md).
 * Les formats diffèrent volontairement : c'est ici, et seulement ici, que chaque fournisseur est connu.
 */
@Component
class WebhookParsers {

    private final JsonMapper json;

    WebhookParsers(JsonMapper json) {
        this.json = json;
    }

    ProviderWebhook parse(String providerCode, byte[] body) {
        try {
            return switch (providerCode) {
                case "BANKILY" -> bankily(json.readValue(body, BankilyEvent.class));
                case "MASRVI" -> masrvi(json.readValue(body, MasrviEvent.class));
                case "VISA_GATEWAY" -> visa(json.readValue(body, VisaEvent.class));
                default -> throw new IllegalArgumentException("Fournisseur inconnu : " + providerCode);
            };
        } catch (JacksonException | DateTimeParseException | NumberFormatException e) {
            throw new MalformedWebhookException("Corps du webhook illisible.");
        }
    }

    private static ProviderWebhook bankily(BankilyEvent e) {
        Outcome outcome = switch (required(e.status(), "status")) {
            case "SUCCESS" -> Outcome.SUCCEEDED;
            case "FAILED" -> Outcome.FAILED;
            default -> Outcome.PENDING;
        };
        return new ProviderWebhook("BANKILY", required(e.eventId(), "eventId"), e.paymentId(), e.merchantReference(),
                outcome, e.status(), e.failureCode(), e.amount(), e.currency(), instant(e.occurredAt()), null, null);
    }

    /** Masrvi : états EN_COURS / SUCCES / ECHEC, codes 00 (ok), 51 (solde), 05 (refus), 91 (délai). */
    private static ProviderWebhook masrvi(MasrviEvent e) {
        Outcome outcome = switch (required(e.etat(), "etat")) {
            case "SUCCES" -> Outcome.SUCCEEDED;
            case "ECHEC" -> Outcome.FAILED;
            default -> Outcome.PENDING;
        };
        String failure = outcome != Outcome.FAILED ? null : switch (String.valueOf(e.code())) {
            case "51" -> "INSUFFICIENT_FUNDS";
            case "91" -> "TIMEOUT";
            default -> "DECLINED";
        };
        return new ProviderWebhook("MASRVI", required(e.evenementId(), "evenementId"), e.idTransaction(),
                e.reference(), outcome, e.etat() + "/" + e.code(), failure,
                e.montant() == null ? null : new BigDecimal(e.montant()), "MRU", instant(e.dateHeure()), null, null);
    }

    private static ProviderWebhook visa(VisaEvent e) {
        Outcome outcome = switch (required(e.status(), "status")) {
            case "AUTHORIZED" -> Outcome.SUCCEEDED;
            case "DECLINED" -> Outcome.FAILED;
            case "EXPIRED" -> Outcome.CANCELLED;
            default -> Outcome.PENDING;
        };
        String failure = e.declineReason() == null ? null
                : "insufficient_funds".equals(e.declineReason()) ? "INSUFFICIENT_FUNDS" : "DECLINED";
        return new ProviderWebhook("VISA_GATEWAY", required(e.id(), "id"), e.sessionId(), e.orderReference(), outcome,
                e.status(), failure, e.amount(), e.currency(),
                e.created() == null ? null : Instant.ofEpochSecond(e.created()),
                e.card() == null ? null : e.card().maskedPan(), e.card() == null ? null : e.card().token());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MalformedWebhookException("Champ « " + field + " » absent du webhook.");
        }
        return value;
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    record BankilyEvent(String eventId, String type, String paymentId, String merchantReference, String status,
            BigDecimal amount, String currency, String failureCode, String occurredAt) {
    }

    record MasrviEvent(String evenementId, String idTransaction, String reference, String etat, String code,
            String montant, String dateHeure) {
    }

    record VisaEvent(String id, String type, String sessionId, String orderReference, String status, BigDecimal amount,
            String currency, Card card, String declineReason, Long created) {

        record Card(String brand, String maskedPan, String token) {
        }
    }

    /** Webhook signé correctement mais mal formé → HTTP 400. */
    static class MalformedWebhookException extends RuntimeException {
        MalformedWebhookException(String message) {
            super(message);
        }
    }
}
