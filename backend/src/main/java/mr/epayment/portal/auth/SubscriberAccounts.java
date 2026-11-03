package mr.epayment.portal.auth;

import java.util.Set;
import java.util.UUID;
import mr.epayment.portal.shared.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Comptes de connexion des abonnés : création dans Keycloak (rôle ABONNE), invitation, activation.
 * Le portail ne manipule jamais de mot de passe : l'abonné définit le sien via le lien d'invitation.
 */
@Service
public class SubscriberAccounts {

    private static final Logger log = LoggerFactory.getLogger(SubscriberAccounts.class);

    private final KeycloakAdminClient keycloak;
    private final AppUserRepository users;
    private final RoleRepository roles;

    SubscriberAccounts(KeycloakAdminClient keycloak, AppUserRepository users, RoleRepository roles) {
        this.keycloak = keycloak;
        this.users = users;
        this.roles = roles;
    }

    /**
     * Crée le compte Keycloak et le compte local. Doit être appelé dans la transaction qui enregistre
     * l'abonné : si elle est annulée, le compte Keycloak est supprimé pour ne pas laisser d'orphelin.
     *
     * @return l'identifiant du compte local ({@code app_user.id})
     */
    @Transactional
    public UUID create(String email, String displayName, String phone) {
        String keycloakId = keycloak.createUser(email, displayName);
        deleteFromKeycloakIfRolledBack(keycloakId);
        keycloak.assignRealmRole(keycloakId, Roles.ABONNE);

        AppUser user = new AppUser(keycloakId);
        user.refreshFrom(email, displayName, roles.findByCodeIn(Set.of(Roles.ABONNE)));
        user.changePhone(phone);
        return users.save(user).getId();
    }

    /**
     * Envoie (ou renvoie) l'email « définissez votre mot de passe ».
     *
     * @return {@code false} si l'envoi a échoué (serveur mail indisponible…) ; l'admin pourra le renvoyer
     */
    public boolean sendInvitation(UUID userId) {
        try {
            keycloak.sendSetPasswordEmail(find(userId).getKeycloakId());
            return true;
        } catch (NotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Envoi de l'invitation impossible pour le compte {}", userId, e);
            return false;
        }
    }

    /** Autorise ou bloque la connexion ; un blocage ferme aussi les sessions en cours. */
    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        AppUser user = find(userId);
        keycloak.setEnabled(user.getKeycloakId(), enabled);
        if (!enabled) {
            keycloak.logout(user.getKeycloakId());
        }
        user.changeEnabled(enabled);
    }

    private AppUser find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Compte inconnu : " + userId));
    }

    private void deleteFromKeycloakIfRolledBack(String keycloakId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        keycloak.deleteUser(keycloakId);
                    } catch (RuntimeException e) {
                        log.error("Compte Keycloak orphelin à supprimer manuellement : {}", keycloakId, e);
                    }
                }
            }
        });
    }
}
