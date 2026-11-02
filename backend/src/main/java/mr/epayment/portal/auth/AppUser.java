package mr.epayment.portal.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Compte local relié à l'identité Keycloak. Le mot de passe et le MFA restent dans Keycloak ;
 * cette table sert aux liens métier (abonné, contrats, audit).
 */
@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    private String keycloakId;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToMany
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {
    }

    AppUser(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    /** Recopie l'identité et les rôles portés par le jeton (Keycloak fait foi). */
    void refreshFrom(String email, String fullName, Set<Role> currentRoles) {
        this.email = email;
        this.fullName = fullName;
        this.roles.clear();
        this.roles.addAll(currentRoles);
        this.updatedAt = Instant.now();
    }

    void markLoggedIn() {
        this.lastLoginAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getFullName() {
        return fullName;
    }

    Instant getLastLoginAt() {
        return lastLoginAt;
    }

    Set<String> roleCodes() {
        Set<String> codes = new HashSet<>();
        roles.forEach(role -> codes.add(role.getCode()));
        return codes;
    }
}
