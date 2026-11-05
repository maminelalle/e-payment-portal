package mr.epayment.portal.subscriber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Demande de changement de téléphone et d'adresse, en attente du code envoyé par email. */
@Entity
@Table(name = "subscriber_contact_change")
class ContactChange {

    static final int MAX_ATTEMPTS = 3;

    enum Status { PENDING, CONFIRMED, CANCELLED, EXPIRED, BLOCKED }

    @Id
    private UUID id;

    @Column(name = "subscriber_id", nullable = false, updatable = false)
    private UUID subscriberId;

    @Column(nullable = false, updatable = false)
    private String phone;

    @Column(updatable = false)
    private String address;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "wrong_attempts", nullable = false)
    private int wrongAttempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected ContactChange() {
    }

    ContactChange(UUID id, UUID subscriberId, String phone, String address, String codeHash, Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.subscriberId = subscriberId;
        this.phone = phone;
        this.address = address;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** @return les essais restants ; à 0 la demande est bloquée */
    int recordWrongCode() {
        wrongAttempts++;
        int remaining = MAX_ATTEMPTS - wrongAttempts;
        if (remaining <= 0) {
            status = Status.BLOCKED;
        }
        return Math.max(remaining, 0);
    }

    void confirm(Instant now) {
        status = Status.CONFIRMED;
        confirmedAt = now;
    }

    void expire() {
        status = Status.EXPIRED;
    }

    void cancel() {
        status = Status.CANCELLED;
    }

    UUID getId() {
        return id;
    }

    UUID getSubscriberId() {
        return subscriberId;
    }

    String getPhone() {
        return phone;
    }

    String getAddress() {
        return address;
    }

    String getCodeHash() {
        return codeHash;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Status getStatus() {
        return status;
    }
}
