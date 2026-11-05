package mr.epayment.portal.subscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import mr.epayment.portal.subscriber.ContactCodeSender.ContactCodeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @MockitoBean
    private ContactCodeSender contactCodes;

    @Autowired
    private JdbcTemplate jdbc;

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

    @Test
    void contactChangeIsAppliedOnlyWithTheEmailedCode() throws Exception {
        String changeId = requestContactChange("22 33 44 55", "Tevragh Zeina, Nouakchott")
                .replaceAll(".*\"changeId\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/abonne/profil").with(subscriber(LAPTOP)))
                .andExpect(jsonPath("$.subscriber.phone").value("+22222000001"));

        mvc.perform(confirm(changeId, "000000")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Code incorrect.")))
                .andExpect(jsonPath("$.remainingAttempts").value(2));
        mvc.perform(confirm(changeId, lastCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+22222334455"))
                .andExpect(jsonPath("$.address").value("Tevragh Zeina, Nouakchott"));

        verify(contactCodes).notifyChanged(any(), anyString(), eq("Société Profil"), eq("+22222334455"),
                eq("Tevragh Zeina, Nouakchott"));
        assertThat(jdbc.queryForList("select action from audit_log where actor_subject = ? order by id", String.class,
                subject)).containsSubsequence("CONTACT_CHANGE_CODE_SENT", "CONTACT_CHANGE_CODE_REJECTED",
                "SUBSCRIBER_CONTACT_UPDATED");
        // Une demande confirmée ne sert pas deux fois.
        mvc.perform(confirm(changeId, lastCode())).andExpect(status().isUnprocessableContent());
    }

    @Test
    void contactChangeIsBlockedAfterThreeWrongCodesAndChecksTheInput() throws Exception {
        mvc.perform(post("/api/abonne/profil/coordonnees").with(subscriber(LAPTOP))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
        mvc.perform(post("/api/abonne/profil/coordonnees").with(subscriber(LAPTOP))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"22000001\"}"))
                .andExpect(status().isUnprocessableContent());

        String changeId = requestContactChange("44556677", null)
                .replaceAll(".*\"changeId\":\"([^\"]+)\".*", "$1");
        // Un autre abonné ne peut pas confirmer cette demande.
        mvc.perform(post("/api/abonne/profil/coordonnees/{id}/confirmation", changeId)
                        .with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ABONNE")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + lastCode() + "\"}"))
                .andExpect(status().is4xxClientError());
        for (int i = 0; i < 3; i++) {
            mvc.perform(confirm(changeId, "999999")).andExpect(status().isUnprocessableContent());
        }
        mvc.perform(confirm(changeId, lastCode())).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/abonne/profil").with(subscriber(LAPTOP)))
                .andExpect(jsonPath("$.subscriber.phone").value("+22222000001"));
        // Nouvelle demande trop tôt : refusée (un code par minute).
        mvc.perform(post("/api/abonne/profil/coordonnees").with(subscriber(LAPTOP))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"44556677\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    private String requestContactChange(String phone, String address) throws Exception {
        String body = address == null ? "{\"phone\":\"%s\"}".formatted(phone)
                : "{\"phone\":\"%s\",\"address\":\"%s\"}".formatted(phone, address);
        return mvc.perform(post("/api/abonne/profil/coordonnees").with(subscriber(LAPTOP))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value(org.hamcrest.Matchers.startsWith("p••••••@")))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.RequestBuilder confirm(String changeId, String code) {
        return post("/api/abonne/profil/coordonnees/{id}/confirmation", changeId).with(subscriber(LAPTOP))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}");
    }

    private String lastCode() {
        ArgumentCaptor<ContactCodeMessage> captor = ArgumentCaptor.forClass(ContactCodeMessage.class);
        verify(contactCodes, org.mockito.Mockito.atLeastOnce()).sendCode(captor.capture());
        return captor.getValue().code();
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
