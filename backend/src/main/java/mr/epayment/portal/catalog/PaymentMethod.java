package mr.epayment.portal.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Moyen de paiement du catalogue (Bankily, Masrvi, Visa…). Le code, le fournisseur et la devise sont fixés à la
 * création : contrats, droits et paiements en dépendent. Le reste se modifie à tout moment.
 */
@Entity
@Table(name = "payment_method")
class PaymentMethod {

    @Id
    private UUID id;

    private String code;

    private String name;

    private String category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private PaymentProvider provider;

    @Column(name = "short_label")
    private String shortLabel;

    private String color;

    @Column(name = "logo_object_key")
    private String logoObjectKey;

    @Column(name = "logo_content_type")
    private String logoContentType;

    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;

    private String description;

    private String instructions;

    private String conditions;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentMethodStatus status;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private long version;

    protected PaymentMethod() {
    }

    /** Nouveau moyen : désactivé, l'admin le vérifie (logo, textes) avant de l'ouvrir. */
    PaymentMethod(String code, PaymentProvider provider, String currency, MethodDetails details) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.provider = provider;
        this.category = provider.getKind();
        this.currency = currency;
        this.status = PaymentMethodStatus.INACTIVE;
        apply(details);
    }

    void update(MethodDetails details) {
        apply(details);
    }

    private void apply(MethodDetails details) {
        this.name = details.name();
        this.shortLabel = details.shortLabel();
        this.color = details.color();
        this.description = details.description();
        this.instructions = details.instructions();
        this.conditions = details.conditions();
        this.updatedAt = Instant.now();
    }

    String getName() {
        return name;
    }

    PaymentProvider getProvider() {
        return provider;
    }

    String getCode() {
        return code;
    }

    PaymentMethodStatus getStatus() {
        return status;
    }

    void changeStatus(PaymentMethodStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    void changeLogo(String objectKey, String contentType) {
        this.logoObjectKey = objectKey;
        this.logoContentType = contentType;
        this.logoUpdatedAt = Instant.now();
        this.updatedAt = logoUpdatedAt;
    }

    void removeLogo() {
        this.logoObjectKey = null;
        this.logoContentType = null;
        this.logoUpdatedAt = Instant.now();
        this.updatedAt = logoUpdatedAt;
    }

    String getLogoObjectKey() {
        return logoObjectKey;
    }

    String getLogoContentType() {
        return logoContentType;
    }

    PaymentMethodView toView() {
        // Adresse publique et versionnée : le navigateur garde le logo en cache jusqu'au prochain changement.
        String logoUrl = logoObjectKey == null ? null
                : "/api/public/moyens/" + code + "/logo?v=" + logoUpdatedAt.toEpochMilli();
        return new PaymentMethodView(id, code, name, category, shortLabel, color, logoUrl, description, instructions,
                conditions, currency, status, provider.toView());
    }
}
