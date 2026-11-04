package mr.epayment.portal.auth;

import java.util.List;
import java.util.Map;

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

    /** Met à jour les informations visibles du compte Keycloak. */
    void updateUser(String userId, String email, String displayName);

    /** Ferme toutes les sessions ouvertes du compte. */
    void logout(String userId);

    void deleteUser(String userId);

    /** Sessions ouvertes du compte (une par navigateur ou appareil connecté). */
    List<UserSession> sessions(String userId);

    /** Ferme une session (déconnecte l'appareil correspondant). */
    void deleteSession(String sessionId);

    /**
     * Session telle que renvoyée par Keycloak.
     *
     * @param start      début, en millisecondes depuis l'époque Unix
     * @param lastAccess dernière activité, même unité
     * @param clients    applications utilisées dans la session (identifiant → nom)
     */
    record UserSession(String id, String ipAddress, long start, long lastAccess, Map<String, String> clients) {
    }
}
