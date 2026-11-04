package mr.epayment.portal.subscriber;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mr.epayment.portal.TestcontainersConfiguration;
import mr.epayment.portal.auth.KeycloakAdminClient;
import mr.epayment.portal.auth.KeycloakAdminClient.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Profil abonné : coordonnées, sessions Keycloak, préférences de notification. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SubscriberProfileTests {

    private static final String LAPTOP = "session-portable";
    private static final String PHONE = "session-telephone";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    @MockitoBean
    private JavaMailSender mailSender;

    private String subject;

    @BeforeEach
    void subscriberWithTwoSessions() throws Exception {
        subject = UUID.randomUUID().toString();
        when(keycloak.createUser(anyString(), anyString())).thenReturn(subject);
        String nif = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"MORALE","email":"profil-%s@exemple.mr","details":{"displayName":"Société Profil","nif":"%s","phone":"22000001"}}"""
                        .formatted(subject.substring(0, 8), nif)))
                .andExpect(status().isCreated());
        long now = System.currentTimeMillis();
        when(keycloak.sessions(subject)).thenReturn(List.of(
                new UserSession(PHONE, "10.0.0.2", now - 7_200_000, now - 3_600_000, Map.of("c", "epayment-frontend")),
                new UserSession(LAPTOP, "10.0.0.1", now - 600_000, now - 1_000, Map.of("c", "epayment-frontend"))));
    }

    @Test
    void profileShowsDetailsAndSessionsMostRecentFirst() throws Exception {
        mvc.perform(get("/api/abonne/profil").with(subscriber(LAPTOP)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriber.displayName").value("Société Profil"))
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].id").value(LAPTOP))
                .andExpect(jsonPath("$.sessions[0].applications[0]").value("epayment-frontend"))
                .andExpect(jsonPath("$.currentSessionId").value(LAPTOP));
    }

    @Test
    void subscriberCanOnlyCloseOwnSessions() throws Exception {
        mvc.perform(delete("/api/abonne/profil/sessions/{id}", PHONE).with(subscriber(LAPTOP)))
                .andExpect(status().isNoContent());
        verify(keycloak).deleteSession(PHONE);

        mvc.perform(delete("/api/abonne/profil/sessions/{id}", "session-d-un-autre").with(subscriber(LAPTOP)))
                .andExpect(status().isNotFound());
        verify(keycloak, never()).deleteSession("session-d-un-autre");

        mvc.perform(post("/api/abonne/profil/sessions/deconnexion").with(subscriber(LAPTOP)))
                .andExpect(status().isNoContent());
        verify(keycloak).logout(subject);
    }

    @Test
    void paymentEmailsCanBeTurnedOff() throws Exception {
        mvc.perform(get("/api/abonne/notifications/preferences").with(subscriber(LAPTOP)))
                .andExpect(jsonPath("$.paymentEmails").value(true));
        mvc.perform(put("/api/abonne/notifications/preferences").with(subscriber(LAPTOP))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentEmails\":false}"))
                .andExpect(jsonPath("$.paymentEmails").value(false));
        mvc.perform(get("/api/abonne/notifications/preferences").with(subscriber(LAPTOP)))
                .andExpect(jsonPath("$.paymentEmails").value(false));
        mvc.perform(get("/api/abonne/notifications").with(subscriber(LAPTOP)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private RequestPostProcessor subscriber(String sessionId) {
        return jwt().jwt(token -> token.subject(subject).claim("sid", sessionId))
                .authorities(new SimpleGrantedAuthority("ROLE_ABONNE"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@epayment.mr"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
