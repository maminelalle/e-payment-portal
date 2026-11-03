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

/** Moyen de paiement du catalogue (Bankily, Masrvi, Visa). */
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

    private String description;

    private String instructions;

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

    PaymentMethodView toView() {
        return new PaymentMethodView(id, code, name, category, shortLabel, color, description, instructions,
                currency, status, provider.toView());
    }
}
