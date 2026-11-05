package mr.epayment.portal.subscriber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import mr.epayment.portal.shared.BusinessRuleException;

@Entity
@Table(name = "subscriber")
class Subscriber {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SubscriberType type;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String nni;

    private String nif;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, updatable = false)
    private String email;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriberStatus status;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Subscriber() {
    }

    Subscriber(String reference, SubscriberType type, SubscriberDetails details, String email, UUID userId) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.type = type;
        this.email = email;
        this.userId = userId;
        this.status = SubscriberStatus.PENDING;
        this.createdAt = Instant.now();
        apply(details);
    }

    void update(SubscriberDetails details) {
        if (status == SubscriberStatus.DISABLED) {
            throw new BusinessRuleException("Un abonné désactivé ne peut plus être modifié.");
        }
        apply(details);
    }

    /** Changement par l'abonné lui-même, après vérification du code : seulement le téléphone et l'adresse. */
    void changeContact(String phone, String address) {
        if (status == SubscriberStatus.DISABLED) {
            throw new BusinessRuleException("Un abonné désactivé ne peut plus être modifié.");
        }
        this.phone = phone;
        this.address = address;
        this.updatedAt = Instant.now();
    }

    void activate() {
        if (status != SubscriberStatus.PENDING && status != SubscriberStatus.SUSPENDED) {
            throw transitionRefused("activer");
        }
        changeStatus(SubscriberStatus.ACTIVE);
    }

    void suspend() {
        if (status != SubscriberStatus.PENDING && status != SubscriberStatus.ACTIVE) {
            throw transitionRefused("suspendre");
        }
        changeStatus(SubscriberStatus.SUSPENDED);
    }

    void disable() {
        if (status == SubscriberStatus.DISABLED) {
            throw transitionRefused("désactiver");
        }
        changeStatus(SubscriberStatus.DISABLED);
    }

    private void apply(SubscriberDetails details) {
        this.displayName = details.displayName();
        this.nni = type == SubscriberType.PHYSIQUE ? details.nni() : null;
        this.nif = type == SubscriberType.MORALE ? details.nif() : null;
        this.phone = details.phone();
        this.address = details.address();
        this.updatedAt = Instant.now();
    }

    private void changeStatus(SubscriberStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    private BusinessRuleException transitionRefused(String action) {
        return new BusinessRuleException("Impossible de " + action + " un abonné au statut " + status + ".");
    }

    UUID getId() {
        return id;
    }

    SubscriberType getType() {
        return type;
    }

    SubscriberStatus getStatus() {
        return status;
    }

    UUID getUserId() {
        return userId;
    }

    String getReference() {
        return reference;
    }

    SubscriberView toView() {
        return new SubscriberView(id, reference, type, displayName, nni, nif, phone, email, address, status,
                createdAt, updatedAt, userId);
    }
}
