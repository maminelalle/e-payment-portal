package mr.epayment.portal.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres {@code epayment.keycloak.*} (voir application.yml). */
@ConfigurationProperties("epayment.keycloak")
record KeycloakProperties(String url, String realm, String invitationClientId, String invitationRedirectUri) {
}
