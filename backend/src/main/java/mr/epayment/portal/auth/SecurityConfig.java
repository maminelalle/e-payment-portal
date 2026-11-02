package mr.epayment.portal.auth;

import java.util.Map;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * API sans état protégée par les jetons JWT de Keycloak.
 * Chaque espace a son préfixe : {@code /api/admin/**} pour ADMIN, {@code /api/abonne/**} pour ABONNE.
 * Le contrôle se fait ici, côté serveur, quel que soit ce qu'affiche le frontend.
 */
@Configuration
class SecurityConfig {

    static final String ACCESS_DENIED = "ACCESS_DENIED";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessDeniedHandler auditedAccessDeniedHandler)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                        .requestMatchers("/api/abonne/**").hasRole(Roles.ABONNE)
                        .requestMatchers("/api/me/**", "/api/me").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .accessDeniedHandler(auditedAccessDeniedHandler))
                .exceptionHandling(errors -> errors.accessDeniedHandler(auditedAccessDeniedHandler))
                .build();
    }

    @Bean
    AccessDeniedHandler auditedAccessDeniedHandler(AuditService auditService) {
        return (request, response, exception) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            auditService.record(AuditEvent.of(authentication, ACCESS_DENIED, "HTTP", request.getRequestURI(),
                    Outcome.FAILURE, Map.of("method", request.getMethod())));
            response.sendError(HttpStatus.FORBIDDEN.value());
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
