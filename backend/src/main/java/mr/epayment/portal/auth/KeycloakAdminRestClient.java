package mr.epayment.portal.auth;

import java.net.URI;
import java.util.List;
import java.util.Map;
import mr.epayment.portal.shared.ConflictException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Implémentation REST de {@link KeycloakAdminClient}. S'authentifie avec le compte de service
 * {@code epayment-backend} (client credentials) ; le jeton est mis en cache et renouvelé par Spring Security.
 */
@Component
@EnableConfigurationProperties(KeycloakProperties.class)
class KeycloakAdminRestClient implements KeycloakAdminClient {

    static final String REGISTRATION_ID = "keycloak-admin";
    private static final List<String> SET_PASSWORD = List.of("UPDATE_PASSWORD");
    /** Durée de validité du lien d'invitation : 72 h. */
    private static final int INVITATION_LIFESPAN_SECONDS = 72 * 3600;

    private final RestClient http;
    private final KeycloakProperties properties;

    KeycloakAdminRestClient(RestClient.Builder builder, KeycloakProperties properties,
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients));
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        this.properties = properties;
        this.http = builder
                .baseUrl(properties.url() + "/admin/realms/" + properties.realm())
                .requestInterceptor(interceptor)
                .build();
    }

    @Override
    public String createUser(String email, String displayName) {
        var user = Map.of(
                "username", email,
                "email", email,
                "emailVerified", true,
                "enabled", true,
                "firstName", displayName,
                "requiredActions", SET_PASSWORD);
        URI location = http.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                        throw new ConflictException("Un compte de connexion existe déjà avec l'email " + email + ".");
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("Création du compte Keycloak impossible : " + response.getStatusCode());
                    }
                    return response.getHeaders().getLocation();
                });
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public void assignRealmRole(String userId, String role) {
        Map<?, ?> roleRepresentation = http.get().uri("/roles/{role}", role).retrieve().body(Map.class);
        http.post().uri("/users/{id}/role-mappings/realm", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", roleRepresentation.get("id"), "name", role)))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void sendSetPasswordEmail(String userId) {
        http.put().uri(uri -> uri.path("/users/{id}/execute-actions-email")
                        .queryParam("client_id", properties.invitationClientId())
                        .queryParam("redirect_uri", properties.invitationRedirectUri())
                        .queryParam("lifespan", INVITATION_LIFESPAN_SECONDS)
                        .build(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(SET_PASSWORD)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void setEnabled(String userId, boolean enabled) {
        http.put().uri("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void updateUser(String userId, String email, String displayName) {
        http.put().uri("/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "username", email, "firstName", displayName))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void logout(String userId) {
        http.post().uri("/users/{id}/logout", userId).retrieve().toBodilessEntity();
    }

    @Override
    public void deleteUser(String userId) {
        http.delete().uri("/users/{id}", userId).retrieve().toBodilessEntity();
    }

    @Override
    public List<UserSession> sessions(String userId) {
        List<UserSession> sessions = http.get().uri("/users/{id}/sessions", userId).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return sessions == null ? List.of() : sessions;
    }

    @Override
    public void deleteSession(String sessionId) {
        http.delete().uri("/sessions/{id}", sessionId).retrieve().toBodilessEntity();
    }
}
