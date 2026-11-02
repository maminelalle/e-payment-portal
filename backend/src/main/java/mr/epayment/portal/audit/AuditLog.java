package mr.epayment.portal.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;

/** Entrée du journal d'audit. Ajout seul : aucune mise à jour ni suppression. */
@Entity
@Table(name = "audit_log")
class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_subject", updatable = false)
    private String actorSubject;

    @Column(name = "actor_label", nullable = false, updatable = false)
    private String actorLabel;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "resource_type", updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private String resourceId;

    @Column(nullable = false, updatable = false)
    private String outcome;

    @Column(name = "ip_address", columnDefinition = "inet", updatable = false)
    @ColumnTransformer(read = "host(ip_address)", write = "cast(? as inet)")
    private String ipAddress;

    @Column(columnDefinition = "jsonb", updatable = false)
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String details;

    protected AuditLog() {
    }

    AuditLog(AuditEvent event, String ipAddress, String detailsJson) {
        this.occurredAt = Instant.now();
        this.actorSubject = event.actorSubject();
        this.actorLabel = event.actorLabel();
        this.action = event.action();
        this.resourceType = event.resourceType();
        this.resourceId = event.resourceId();
        this.outcome = event.outcome().name();
        this.ipAddress = ipAddress;
        this.details = detailsJson;
    }

    AuditEntry toEntry() {
        return new AuditEntry(id, occurredAt, actorSubject, actorLabel, action, resourceType, resourceId,
                outcome, ipAddress, details);
    }
}
