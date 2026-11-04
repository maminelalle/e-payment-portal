package mr.epayment.portal.subscriber;

import java.util.List;
import java.util.Map;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.auth.SubscriberAccounts;
import mr.epayment.portal.auth.SubscriberAccounts.AccountSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil de l'abonné connecté (écran B5) : coordonnées (lecture seule : elles figurent au contrat, l'admin
 * les modifie) et sessions ouvertes. Le mot de passe se change chez Keycloak, jamais via le portail.
 */
@RestController
@RequestMapping("/api/abonne/profil")
class SubscriberProfileController {

    private final CurrentSubscriber currentSubscriber;
    private final SubscriberAccounts accounts;
    private final AuditService auditService;

    SubscriberProfileController(CurrentSubscriber currentSubscriber, SubscriberAccounts accounts,
            AuditService auditService) {
        this.currentSubscriber = currentSubscriber;
        this.accounts = accounts;
        this.auditService = auditService;
    }

    @GetMapping
    Profile profile(Authentication authentication) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        return new Profile(subscriber, accounts.sessions(subscriber.accountId()), currentSessionId(authentication));
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> endSession(Authentication authentication, @PathVariable String sessionId) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        accounts.endSession(subscriber.accountId(), sessionId);
        audit(authentication, "SESSION_ENDED", subscriber, Map.of("session", sessionId));
        return ResponseEntity.noContent().build();
    }

    /** Déconnexion de tous les appareils, y compris celui-ci (le portail renvoie ensuite vers la connexion). */
    @PostMapping("/sessions/deconnexion")
    ResponseEntity<Void> logoutEverywhere(Authentication authentication) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        audit(authentication, "SESSIONS_ALL_ENDED", subscriber, Map.of());
        accounts.logoutEverywhere(subscriber.accountId());
        return ResponseEntity.noContent().build();
    }

    /** Claim {@code sid} du jeton : la session du navigateur qui fait la demande. */
    private static String currentSessionId(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaimAsString("sid") : null;
    }

    private void audit(Authentication authentication, String action, SubscriberView subscriber,
            Map<String, Object> details) {
        auditService.record(AuditEvent.of(authentication, action, "SUBSCRIBER", subscriber.reference(),
                Outcome.SUCCESS, details));
    }

    /** @param currentSessionId session du navigateur courant (signalée « cet appareil ») */
    record Profile(SubscriberView subscriber, List<AccountSession> sessions, String currentSessionId) {
    }
}
