package mr.epayment.portal.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fournisseur technique (API Bankily, Masrvi, passerelle carte). Aucun secret n'est stocké ici. */
@Entity
@Table(name = "payment_provider")
class PaymentProvider {

    @Id
    private UUID id;

    private String code;

    private String name;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "credentials_ref")
    private String credentialsRef;

    private String kind;

    private String status;

    @Column(name = "health_status")
    private String healthStatus;

    @Column(name = "last_health_check_at")
    private Instant lastHealthCheckAt;

    protected PaymentProvider() {
    }

    PaymentMethodView.Provider toView() {
        return new PaymentMethodView.Provider(code, name, kind, status, healthStatus, lastHealthCheckAt);
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getKind() {
        return kind;
    }

    /** Raccordé : un adaptateur existe et le fournisseur est en service (Sedad ne l'est pas encore). */
    boolean isEnabled() {
        return "ENABLED".equals(status);
    }
}
