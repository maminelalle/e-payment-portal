package mr.epayment.portal.auth;

/**
 * Accès à l'API d'administration Keycloak (comptes utilisateurs du realm).
 * Utilisé uniquement par {@link SubscriberAccounts} ; public pour pouvoir être simulé dans les tests.
 */
public interface KeycloakAdminClient {

    /**
     * Crée un compte sans mot de passe (action « définir le mot de passe » en attente).
     *
     * @return l'identifiant Keycloak du compte
     * @throws mr.epayment.portal.shared.ConflictException si l'email est déjà utilisé dans Keycloak
     */
    String createUser(String email, String displayName);

    void assignRealmRole(String userId, String role);

    /** Envoie l'email « définissez votre mot de passe » ; le lien ramène sur le portail. */
    void sendSetPasswordEmail(String userId);

    void setEnabled(String userId, boolean enabled);

    /** Ferme toutes les sessions ouvertes du compte. */
    void logout(String userId);

    void deleteUser(String userId);
}
