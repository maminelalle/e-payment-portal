package mr.epayment.portal.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import mr.epayment.portal.gateway.PaymentProvider.Outcome;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Paiement d'un abonné. Statut interne du portail et statut brut du fournisseur sont séparés :
 * le premier pilote le portail, le second reste la trace exacte de ce qu'a dit le fournisseur.
 */
@Entity
@Table(name = "payment_transaction")
class PaymentTransaction {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "subscriber_id", nullable = false, updatable = false)
    private UUID subscriberId;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private UUID paymentMethodId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    /** Taux de commission du contrat qui autorise le paiement, figé à la création (null : aucune commission). */
    @Column(name = "commission_rate", updatable = false)
    private BigDecimal commissionRate;

    /** Commission due : calculée quand le paiement réussit (contrat, article 4). */
    @Column(name = "commission_amount")
    private BigDecimal commissionAmount;

    @Column(name = "provider_status")
    private String providerStatus;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "payer_phone", updatable = false)
    private String payerPhone;

    @Column(name = "card_masked_pan")
    private String cardMaskedPan;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(updatable = false)
    private String description;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "receipt_object_key")
    private String receiptObjectKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected PaymentTransaction() {
    }

    PaymentTransaction(String reference, UUID subscriberId, UUID paymentMethodId, UUID contractId, BigDecimal amount,
            String currency, String idempotencyKey, String payerPhone, String description, BigDecimal commissionRate) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.subscriberId = subscriberId;
        this.paymentMethodId = paymentMethodId;
        this.contractId = contractId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.payerPhone = payerPhone;
        this.description = description;
        this.commissionRate = commissionRate;
        this.status = TransactionStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /**
     * Applique ce que dit le fournisseur (réponse à l'initiation, webhook ou consultation du statut).
     * Une transaction terminée ne change plus : un message tardif ou dupliqué est sans effet.
     *
     * @return vrai si la transaction a changé
     */
    boolean applyProviderUpdate(Outcome outcome, String providerReference, String providerStatus, String failureCode,
            String maskedPan, String redirectUrl) {
        if (status.isFinal()) {
            return false;
        }
        if (providerReference != null) {
            this.providerReference = providerReference;
        }
        this.providerStatus = providerStatus;
        if (maskedPan != null) {
            this.cardMaskedPan = maskedPan;
        }
        this.status = switch (outcome) {
            case SUCCEEDED -> TransactionStatus.SUCCEEDED;
            case FAILED -> TransactionStatus.FAILED;
            case CANCELLED -> TransactionStatus.CANCELLED;
            case PENDING -> TransactionStatus.PENDING;
        };
        // La page hébergée (carte) ne sert que tant que le paiement est en cours.
        if (status != TransactionStatus.PENDING) {
            this.redirectUrl = null;
        } else if (redirectUrl != null) {
            this.redirectUrl = redirectUrl;
        }
        if (status == TransactionStatus.SUCCEEDED && commissionRate != null) {
            this.commissionAmount = commission(amount, commissionRate);
        }
        if (status == TransactionStatus.FAILED) {
            this.failureCode = failureCode == null ? "DECLINED" : failureCode;
            this.failureMessage = FailureMessages.of(this.failureCode);
        }
        this.updatedAt = Instant.now();
        if (status.isFinal()) {
            this.completedAt = updatedAt;
        }
        return true;
    }

    /** Le fournisseur n'a pas pu être joint : rien n'a été débité. */
    void markProviderUnavailable() {
        this.status = TransactionStatus.FAILED;
        this.failureCode = "PROVIDER_UNAVAILABLE";
        this.failureMessage = FailureMessages.of(failureCode);
        this.updatedAt = Instant.now();
        this.completedAt = updatedAt;
    }

    /** Abandon par l'abonné d'une session carte non payée. */
    boolean cancel() {
        if (status.isFinal()) {
            return false;
        }
        this.status = TransactionStatus.CANCELLED;
        this.redirectUrl = null;
        this.updatedAt = Instant.now();
        this.completedAt = updatedAt;
        return true;
    }

    /** Montant × taux, arrondi au centime le plus proche (0,005 → 0,01). */
    static BigDecimal commission(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    BigDecimal getCommissionRate() {
        return commissionRate;
    }

    BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    void attachReceipt(String objectKey) {
        this.receiptObjectKey = objectKey;
        this.updatedAt = Instant.now();
    }

    String getReceiptObjectKey() { return receiptObjectKey; }
    UUID getId() { return id; }
    String getReference() { return reference; }
    UUID getSubscriberId() { return subscriberId; }
    UUID getPaymentMethodId() { return paymentMethodId; }
    UUID getContractId() { return contractId; }
    BigDecimal getAmount() { return amount; }
    String getCurrency() { return currency; }
    TransactionStatus getStatus() { return status; }
    String getProviderStatus() { return providerStatus; }
    String getProviderReference() { return providerReference; }
    String getPayerPhone() { return payerPhone; }
    String getCardMaskedPan() { return cardMaskedPan; }
    String getRedirectUrl() { return redirectUrl; }
    String getDescription() { return description; }
    String getFailureCode() { return failureCode; }
    String getFailureMessage() { return failureMessage; }
    Instant getCreatedAt() { return createdAt; }
    Instant getCompletedAt() { return completedAt; }
}
