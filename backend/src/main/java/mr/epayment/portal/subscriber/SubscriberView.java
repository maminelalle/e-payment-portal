package mr.epayment.portal.subscriber;

import java.time.Instant;
import java.util.UUID;

/** Abonné tel qu'exposé par l'API d'administration. */
public record SubscriberView(
        UUID id,
        String reference,
        SubscriberType type,
        String displayName,
        String nni,
        String nif,
        String phone,
        String email,
        String address,
        SubscriberStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID accountId) {
}
