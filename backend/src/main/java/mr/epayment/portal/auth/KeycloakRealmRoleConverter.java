package mr.epayment.portal.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Transforme les rôles du realm ({@code realm_access.roles}) en autorités {@code ROLE_ADMIN} / {@code ROLE_ABONNE}.
 * Les rôles techniques de Keycloak (offline_access…) sont ignorés.
 */
class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(Roles.ALL::contains)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
