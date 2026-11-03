package mr.epayment.portal.signature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tentative de signature d'un contrat par code à usage unique, puis preuve de la signature. */
@Entity
@Table(name = "signature")
class ContractSignature {

    static final int MAX_ATTEMPTS = 3;

    enum Status { PENDING, SIGNED, FAILED, EXPIRED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "signer_user_id", nullable = false, updatable = false)
    private UUID signerUserId;

    @Column(nullable = false, updatable = false)
    private String method = "OTP_EMAIL";

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "otp_attempts", nullable = false)
    private short otpAttempts;

    @Column(name = "otp_sent_count", nullable = false)
    private short otpSentCount;

    @Column(name = "otp_last_sent_at", nullable = false)
    private Instant otpLastSentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "document_sha256", length = 64, nullable = false, updatable = false)
    private String documentSha256;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "signed_document_sha256", length = 64)
    private String signedDocumentSha256;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(read = "host(ip_address)", write = "cast(? as inet)")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContractSignature() {
    }

    ContractSignature(UUID contractId, UUID signerUserId, String documentSha256) {
        this.id = UUID.randomUUID();
        this.contractId = contractId;
        this.signerUserId = signerUserId;
        this.documentSha256 = documentSha256;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    /** Nouveau code : remplace le précédent, remet les essais à zéro. */
    void issueCode(String otpHash, Instant now, Instant expiresAt) {
        this.otpHash = otpHash;
        this.otpAttempts = 0;
        this.otpExpiresAt = expiresAt;
        this.otpSentCount = (short) (otpLastSentAt == null ? 1 : otpSentCount + 1);
        this.otpLastSentAt = now;
    }

    /** @return le nombre d'essais restants après cet échec */
    int recordWrongCode() {
        otpAttempts++;
        if (otpAttempts >= MAX_ATTEMPTS) {
            status = Status.FAILED;
        }
        return MAX_ATTEMPTS - otpAttempts;
    }

    void expire() {
        status = Status.EXPIRED;
    }

    void cancel() {
        status = Status.CANCELLED;
    }

    void markSigned(String signedDocumentSha256, Instant signedAt, String ipAddress, String userAgent) {
        this.status = Status.SIGNED;
        this.signedDocumentSha256 = signedDocumentSha256;
        this.signedAt = signedAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    UUID getId() {
        return id;
    }

    UUID getContractId() {
        return contractId;
    }

    UUID getSignerUserId() {
        return signerUserId;
    }

    String getOtpHash() {
        return otpHash;
    }

    Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    Instant getOtpLastSentAt() {
        return otpLastSentAt;
    }

    int getOtpSentCount() {
        return otpSentCount;
    }

    Status getStatus() {
        return status;
    }

    String getDocumentSha256() {
        return documentSha256;
    }
}
