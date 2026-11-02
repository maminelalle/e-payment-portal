package mr.epayment.portal.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Utilisateur connecté, tel que renvoyé par {@code /api/me}. */
public record CurrentUser(UUID id, String email, String fullName, Set<String> roles, Instant lastLoginAt) {
}
