package mr.epayment.portal.audit;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Événement à journaliser. Les détails ne doivent contenir ni secret, ni donnée de carte, ni code OTP.
 *
 * @param actorSubject identifiant Keycloak de l'acteur, ou {@code null} pour le système
 * @param actorLabel   email de l'acteur, ou {@code SYSTEM}
 */
public record AuditEvent(
        String actorSubject,
        String actorLabel,
        String action,
        String resourceType,
        String resourceId,
        Outcome outcome,
        Map<String, Object> details) {

    public enum Outcome { SUCCESS, FAILURE }

    public static final String SYSTEM = "SYSTEM";

    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** Événement dont l'acteur est l'utilisateur authentifié (jeton Keycloak), ou le système. */
    public static AuditEvent of(Authentication authentication, String action, String resourceType,
            String resourceId, Outcome outcome, Map<String, Object> details) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuditEvent(null, SYSTEM, action, resourceType, resourceId, outcome, details);
        }
        String label = authentication.getName();
        if (authentication.getPrincipal() instanceof Jwt jwt && jwt.hasClaim("email")) {
            label = jwt.getClaimAsString("email");
        }
        return new AuditEvent(authentication.getName(), label, action, resourceType, resourceId, outcome, details);
    }
}
