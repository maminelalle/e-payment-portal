package mr.epayment.portal.catalog;

import java.time.Instant;
import java.util.UUID;

/** Moyen de paiement tel qu'exposé par l'API (et aux autres modules). */
public record PaymentMethodView(
        UUID id,
        String code,
        String name,
        String category,
        String shortLabel,
        String color,
        String description,
        String instructions,
        String currency,
        PaymentMethodStatus status,
        Provider provider) {

    public record Provider(String code, String name, String status, String healthStatus, Instant lastHealthCheckAt) {
    }
}
