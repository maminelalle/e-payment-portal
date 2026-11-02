package mr.epayment.portal.audit;

import java.time.Instant;

/** Entrée du journal telle qu'exposée par l'API d'administration. */
public record AuditEntry(
        Long id,
        Instant occurredAt,
        String actorSubject,
        String actorLabel,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String ipAddress,
        String details) {
}
