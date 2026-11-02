package mr.epayment.portal.auth;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserAccountService {

    static final String LOGIN = "LOGIN";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final AuditService auditService;

    UserAccountService(AppUserRepository users, RoleRepository roles, AuditService auditService) {
        this.users = users;
        this.roles = roles;
        this.auditService = auditService;
    }

    /**
     * Appelé une fois par le frontend juste après la connexion OIDC : crée ou met à jour le compte local
     * à partir du jeton, mémorise la date de connexion et journalise l'événement LOGIN.
     */
    @Transactional
    CurrentUser recordLogin(JwtAuthenticationToken authentication) {
        var jwt = authentication.getToken();
        Set<String> roleCodes = roleCodes(authentication);

        AppUser user = users.findByKeycloakId(jwt.getSubject()).orElseGet(() -> new AppUser(jwt.getSubject()));
        user.refreshFrom(jwt.getClaimAsString("email"), fullName(jwt.getClaims()), roles.findByCodeIn(roleCodes));
        user.markLoggedIn();
        users.saveAndFlush(user);

        auditService.record(AuditEvent.of(authentication, LOGIN, "USER", user.getId().toString(), Outcome.SUCCESS,
                Map.of("roles", roleCodes)));
        return toCurrentUser(user);
    }

    /** Profil courant, lu dans le jeton ; l'identifiant local est renseigné si le compte existe déjà. */
    @Transactional(readOnly = true)
    CurrentUser currentUser(JwtAuthenticationToken authentication) {
        var jwt = authentication.getToken();
        return users.findByKeycloakId(jwt.getSubject())
                .map(UserAccountService::toCurrentUser)
                .orElseGet(() -> new CurrentUser(null, jwt.getClaimAsString("email"), fullName(jwt.getClaims()),
                        roleCodes(authentication), null));
    }

    private static Set<String> roleCodes(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String fullName(Map<String, Object> claims) {
        if (claims.get("name") instanceof String name && !name.isBlank()) {
            return name;
        }
        return String.valueOf(claims.getOrDefault("email", claims.get("sub")));
    }

    private static CurrentUser toCurrentUser(AppUser user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getFullName(), user.roleCodes(),
                user.getLastLoginAt());
    }
}
