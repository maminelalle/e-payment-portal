package mr.epayment.portal.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Droit d'un abonné sur un moyen de paiement, issu d'un contrat signé et activé. */
@Entity
@Table(name = "subscriber_entitlement")
class Entitlement {

    enum Status { ACTIVE, SUSPENDED, REVOKED, EXPIRED }

    @Id
    private UUID id;

    @Column(name = "subscriber_id", nullable = false, updatable = false)
    private UUID subscriberId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private UUID paymentMethodId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private LocalDate validTo;

    @Column(name = "per_transaction_limit", nullable = false, updatable = false)
    private BigDecimal perTransactionLimit;

    @Column(name = "monthly_limit", nullable = false, updatable = false)
    private BigDecimal monthlyLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason")
    private String revokeReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Entitlement() {
    }

    /** @return vrai si le statut a changé */
    boolean changeStatus(Status newStatus, String reason) {
        if (status == newStatus) {
            return false;
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
        if (newStatus == Status.REVOKED || newStatus == Status.EXPIRED) {
            this.revokedAt = updatedAt;
            this.revokeReason = reason;
        }
        return true;
    }

    boolean coversDate(LocalDate day) {
        return !day.isBefore(validFrom) && !day.isAfter(validTo);
    }

    UUID getId() {
        return id;
    }

    UUID getContractId() {
        return contractId;
    }

    UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    LocalDate getValidFrom() {
        return validFrom;
    }

    LocalDate getValidTo() {
        return validTo;
    }

    BigDecimal getPerTransactionLimit() {
        return perTransactionLimit;
    }

    BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    Status getStatus() {
        return status;
    }

    Instant getActivatedAt() {
        return activatedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    String getRevokeReason() {
        return revokeReason;
    }
}
