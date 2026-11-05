package mr.epayment.portal.subscriber;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.auth.SubscriberAccounts;
import mr.epayment.portal.auth.SubscriberAccounts.AccountSession;
import mr.epayment.portal.subscriber.ContactChangeService.ContactChallenge;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil de l'abonné connecté (écran B5) : coordonnées et sessions ouvertes. L'abonné change lui-même son
 * téléphone et son adresse, confirmés par un code envoyé par email ; le nom, le NNI/NIF et l'email restent
 * modifiés par l'admin. Le mot de passe se change chez Keycloak, jamais via le portail.
 */
@RestController
@RequestMapping("/api/abonne/profil")
class SubscriberProfileController {

    private final CurrentSubscriber currentSubscriber;
    private final SubscriberAccounts accounts;
    private final AuditService auditService;
    private final ContactChangeService contactChanges;

    SubscriberProfileController(CurrentSubscriber currentSubscriber, SubscriberAccounts accounts,
            AuditService auditService, ContactChangeService contactChanges) {
        this.currentSubscriber = currentSubscriber;
        this.accounts = accounts;
        this.auditService = auditService;
        this.contactChanges = contactChanges;
    }

    /** Étape 1 du changement de coordonnées : le code part à l'email du compte. */
    @PostMapping("/coordonnees")
    ContactChallenge requestContactChange(Authentication authentication, @Valid @RequestBody NewContact body) {
        return contactChanges.requestChange(authentication, body.phone(), body.address());
    }

    /** Étape 2 : le code reçu applique le changement. */
    @PostMapping("/coordonnees/{changeId}/confirmation")
    SubscriberView confirmContactChange(Authentication authentication, @PathVariable UUID changeId,
            @Valid @RequestBody Confirmation body) {
        return contactChanges.confirm(authentication, changeId, body.code());
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

    @ExceptionHandler(ContactCodeRejectedException.class)
    ProblemDetail rejected(ContactCodeRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setProperty("remainingAttempts", e.remainingAttempts());
        return problem;
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

    record NewContact(
            // Format vérifié après normalisation (« 22 00 00 01 », « 0022222000001 »…), comme côté admin.
            @NotBlank(message = "Le téléphone est obligatoire.") String phone,
            @Size(max = 300, message = "300 caractères au maximum.") String address) {
    }

    record Confirmation(
            @NotBlank(message = "Saisissez le code reçu.")
            @Pattern(regexp = "\\d{6}", message = "Le code comporte 6 chiffres.") String code) {
    }

    /** @param currentSessionId session du navigateur courant (signalée « cet appareil ») */
    record Profile(SubscriberView subscriber, List<AccountSession> sessions, String currentSessionId) {
    }
}
