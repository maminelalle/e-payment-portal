package mr.epayment.portal.contract;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mr.epayment.portal.shared.BusinessRuleException;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contract")
class Contract {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "subscriber_id", nullable = false, updatable = false)
    private UUID subscriberId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    /** Contrat que cet avenant remplacera à son activation ; null pour un contrat initial. */
    @Column(name = "parent_contract_id", updatable = false)
    private UUID parentContractId;

    /** 0 pour le contrat initial, n pour l'avenant n°n. */
    @Column(name = "amendment_number", nullable = false, updatable = false)
    private int amendmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "commission_rate")
    private BigDecimal commissionRate;

    @ElementCollection
    @CollectionTable(name = "contract_payment_method", joinColumns = @JoinColumn(name = "contract_id"))
    @BatchSize(size = 50)
    private List<IncludedMethod> methods = new ArrayList<>();

    @Column(name = "pdf_object_key")
    private String pdfObjectKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pdf_sha256", length = 64)
    private String pdfSha256;

    @Column(name = "signed_pdf_object_key")
    private String signedPdfObjectKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "signed_pdf_sha256", length = 64)
    private String signedPdfSha256;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "termination_reason")
    private String terminationReason;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Contract() {
    }

    Contract(String reference, UUID subscriberId, UUID templateId, String createdBy) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.subscriberId = subscriberId;
        this.templateId = templateId;
        this.createdBy = createdBy;
        this.status = ContractStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /**
     * Avenant au contrat {@code parent} : brouillon prérempli avec ses moyens, plafonds, commission et fin de
     * validité ; il prend effet à son activation (au plus tôt aujourd'hui).
     */
    static Contract amendmentOf(Contract parent, String reference, int number, UUID templateId, LocalDate today,
            String createdBy) {
        Contract amendment = new Contract(reference, parent.subscriberId, templateId, createdBy);
        amendment.parentContractId = parent.id;
        amendment.amendmentNumber = number;
        amendment.startDate = parent.startDate.isAfter(today) ? parent.startDate : today;
        amendment.endDate = parent.endDate;
        amendment.commissionRate = parent.commissionRate;
        parent.methods.forEach(m -> amendment.methods.add(
                new IncludedMethod(m.paymentMethodId(), m.perTransactionLimit(), m.monthlyLimit())));
        return amendment;
    }

    /** Un brouillon peut être complété en plusieurs fois ; les contrôles complets ont lieu à l'envoi. */
    void updateDraft(UUID templateId, LocalDate startDate, LocalDate endDate, BigDecimal commissionRate,
            List<IncludedMethod> methods) {
        requireDraft("modifier");
        this.templateId = templateId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.commissionRate = commissionRate;
        this.methods.clear();
        this.methods.addAll(methods);
        this.updatedAt = Instant.now();
    }

    /** Fige le contrat : son PDF et l'empreinte SHA-256 font désormais foi. */
    void markSentForSignature(String pdfObjectKey, String pdfSha256) {
        requireDraft("envoyer");
        this.pdfObjectKey = pdfObjectKey;
        this.pdfSha256 = pdfSha256;
        this.status = ContractStatus.PENDING_SIGNATURE;
        this.sentAt = Instant.now();
        this.updatedAt = sentAt;
    }

    /** Signature vérifiée : la version scellée fait foi ; le contrat attend son activation (semaine 7). */
    void markSigned(String signedPdfObjectKey, String signedPdfSha256) {
        if (status != ContractStatus.PENDING_SIGNATURE) {
            throw new BusinessRuleException("Le contrat " + reference + " n'est pas en attente de signature.");
        }
        this.signedPdfObjectKey = signedPdfObjectKey;
        this.signedPdfSha256 = signedPdfSha256;
        this.status = ContractStatus.SIGNED;
        this.signedAt = Instant.now();
        this.updatedAt = signedAt;
    }

    /** Signé → actif. Sans effet si le contrat est déjà actif (activation idempotente). */
    boolean activate() {
        if (status == ContractStatus.ACTIVE) {
            return false;
        }
        requireStatus(ContractStatus.SIGNED, "activer");
        changeStatus(ContractStatus.ACTIVE);
        this.activatedAt = updatedAt;
        return true;
    }

    void suspend() {
        requireStatus(ContractStatus.ACTIVE, "suspendre");
        changeStatus(ContractStatus.SUSPENDED);
    }

    void resume() {
        requireStatus(ContractStatus.SUSPENDED, "réactiver");
        changeStatus(ContractStatus.ACTIVE);
    }

    /** Remplacé par l'avenant {@code amendmentReference}, activé : le contrat n'est plus en vigueur. */
    void replaceBy(String amendmentReference) {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.SUSPENDED) {
            throw new BusinessRuleException("Le contrat " + reference + " n'est pas en vigueur.");
        }
        changeStatus(ContractStatus.REPLACED);
        this.terminatedAt = updatedAt;
        this.terminationReason = "Remplacé par l'avenant " + amendmentReference;
    }

    /** Résiliation : possible tant que le contrat n'est pas déjà terminé (expiré, résilié ou remplacé). */
    void terminate(String reason) {
        if (status == ContractStatus.DRAFT || status == ContractStatus.EXPIRED
                || status == ContractStatus.TERMINATED || status == ContractStatus.REPLACED) {
            throw new BusinessRuleException("Impossible de résilier le contrat " + reference + " au statut "
                    + status + ".");
        }
        changeStatus(ContractStatus.TERMINATED);
        this.terminatedAt = updatedAt;
        this.terminationReason = reason;
    }

    void expire() {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.SUSPENDED) {
            throw new BusinessRuleException("Le contrat " + reference + " n'est pas en vigueur.");
        }
        changeStatus(ContractStatus.EXPIRED);
    }

    private void requireStatus(ContractStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleException("Impossible de " + action + " le contrat " + reference + " : il est "
                    + status + ".");
        }
    }

    private void changeStatus(ContractStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    Instant getActivatedAt() {
        return activatedAt;
    }

    Instant getTerminatedAt() {
        return terminatedAt;
    }

    String getTerminationReason() {
        return terminationReason;
    }

    boolean isSigned() {
        return signedPdfObjectKey != null;
    }

    void requireDraft(String action) {
        if (status != ContractStatus.DRAFT) {
            throw new BusinessRuleException("Impossible de " + action + " le contrat " + reference
                    + " : il n'est plus à l'état de brouillon.");
        }
    }

    UUID getId() {
        return id;
    }

    String getReference() {
        return reference;
    }

    UUID getSubscriberId() {
        return subscriberId;
    }

    UUID getTemplateId() {
        return templateId;
    }

    UUID getParentContractId() {
        return parentContractId;
    }

    int getAmendmentNumber() {
        return amendmentNumber;
    }

    ContractStatus getStatus() {
        return status;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    BigDecimal getCommissionRate() {
        return commissionRate;
    }

    List<IncludedMethod> getMethods() {
        return List.copyOf(methods);
    }

    String getPdfObjectKey() {
        return pdfObjectKey;
    }

    String getPdfSha256() {
        return pdfSha256;
    }

    Instant getSentAt() {
        return sentAt;
    }

    String getSignedPdfObjectKey() {
        return signedPdfObjectKey;
    }

    String getSignedPdfSha256() {
        return signedPdfSha256;
    }

    Instant getSignedAt() {
        return signedAt;
    }

    String getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
