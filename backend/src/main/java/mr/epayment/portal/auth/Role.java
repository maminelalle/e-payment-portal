package mr.epayment.portal.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Copie locale des rôles Keycloak (référentiel figé par la migration V1). */
@Entity
@Table(name = "role")
class Role {

    @Id
    private Short id;

    private String code;

    private String label;

    protected Role() {
    }

    String getCode() {
        return code;
    }
}
