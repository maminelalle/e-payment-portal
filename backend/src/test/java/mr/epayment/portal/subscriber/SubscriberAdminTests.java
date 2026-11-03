package mr.epayment.portal.subscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mr.epayment.portal.TestcontainersConfiguration;
import mr.epayment.portal.auth.KeycloakAdminClient;
import mr.epayment.portal.auth.Roles;
import mr.epayment.portal.shared.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;

/** Gestion des abonnés de bout en bout (API → base), Keycloak étant simulé. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SubscriberAdminTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    private String keycloakId;

    @BeforeEach
    void keycloakCreatesAccounts() {
        keycloakId = UUID.randomUUID().toString();
        when(keycloak.createUser(anyString(), anyString())).thenReturn(keycloakId);
    }

    @Test
    void adminCreatesNaturalPersonWithLoginAccountAndInvitation() throws Exception {
        String email = uniqueEmail();

        createPhysique(email, randomDigits(10), "36 00 00 03")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationSent").value(true))
                .andExpect(jsonPath("$.subscriber.reference").value(org.hamcrest.Matchers.matchesPattern("ABN-\\d{4,}")))
                .andExpect(jsonPath("$.subscriber.status").value("PENDING"))
                .andExpect(jsonPath("$.subscriber.phone").value("+22236000003"))
                .andExpect(jsonPath("$.subscriber.email").value(email));

        verify(keycloak).createUser(email, "Ahmed Salem");
        verify(keycloak).assignRealmRole(keycloakId, Roles.ABONNE);
        verify(keycloak).sendSetPasswordEmail(keycloakId);
        assertThat(jdbc.queryForObject("""
                select r.code from subscriber s
                join app_user u on u.id = s.user_id
                join user_role ur on ur.user_id = u.id
                join role r on r.id = ur.role_id
                where s.email = ? and u.keycloak_id = ?""", String.class, email, keycloakId)).isEqualTo(Roles.ABONNE);
        assertThat(jdbc.queryForList("select action from audit_log where details->>'reference' = "
                + "(select reference from subscriber where email = ?)", String.class, email))
                .contains("SUBSCRIBER_CREATED");
    }

    @Test
    void legalEntityRequiresNifAndValidFields() throws Exception {
        mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"MORALE","email":"pas-un-email","details":{"displayName":"Société ABC","phone":"12345"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());

        mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"MORALE","email":"%s","details":{"displayName":"Société ABC","phone":"22000001"}}"""
                .formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nif").exists());

        verify(keycloak, never()).createUser(anyString(), anyString());
    }

    @Test
    void duplicateEmailIsRejectedBeforeCallingKeycloak() throws Exception {
        String email = uniqueEmail();
        createPhysique(email, randomDigits(10), "22000001").andExpect(status().isCreated());

        createPhysique(email.toUpperCase(), randomDigits(10), "22000002").andExpect(status().isConflict());

        verify(keycloak).createUser(anyString(), anyString());
    }

    @Test
    void emailAlreadyKnownByKeycloakIsAConflict() throws Exception {
        String email = uniqueEmail();
        when(keycloak.createUser(eq(email), anyString())).thenThrow(new ConflictException("existe déjà"));

        createPhysique(email, randomDigits(10), "22000001").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from subscriber where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void keycloakAccountIsDeletedWhenCreationFailsMidway() throws Exception {
        doThrow(new IllegalStateException("Keycloak indisponible")).when(keycloak).assignRealmRole(anyString(), anyString());
        String email = uniqueEmail();

        createPhysique(email, randomDigits(10), "22000001").andExpect(status().isInternalServerError());

        verify(keycloak).deleteUser(keycloakId);
        assertThat(jdbc.queryForObject("select count(*) from app_user where keycloak_id = ?", Integer.class, keycloakId))
                .isZero();
    }

    @Test
    void subscriberIsCreatedEvenIfInvitationEmailFails() throws Exception {
        doThrow(new IllegalStateException("SMTP indisponible")).when(keycloak).sendSetPasswordEmail(anyString());

        createPhysique(uniqueEmail(), randomDigits(10), "22000001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationSent").value(false));
    }

    @Test
    void statusLifecycleBlocksAndRestoresLogin() throws Exception {
        String id = createdId(createPhysique(uniqueEmail(), randomDigits(10), "22000001"));

        mvc.perform(post("/api/admin/abonnes/{id}/activer", id).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(keycloak, never()).setEnabled(anyString(), any(Boolean.class));

        mvc.perform(post("/api/admin/abonnes/{id}/suspendre", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Impayés\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        verify(keycloak).setEnabled(keycloakId, false);
        verify(keycloak).logout(keycloakId);

        mvc.perform(post("/api/admin/abonnes/{id}/activer", id).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(keycloak).setEnabled(keycloakId, true);

        mvc.perform(post("/api/admin/abonnes/{id}/desactiver", id).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(post("/api/admin/abonnes/{id}/activer", id).with(admin()))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/admin/abonnes/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"X\",\"nni\":\"%s\",\"phone\":\"22000001\"}".formatted(randomDigits(10))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void updateChangesDetailsButNotEmail() throws Exception {
        String email = uniqueEmail();
        String id = createdId(createPhysique(email, randomDigits(10), "22000001"));

        mvc.perform(put("/api/admin/abonnes/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ahmed Salem Ould","nni":"%s","phone":"+222 46 00 00 04","address":"Tevragh Zeina"}"""
                                .formatted(randomDigits(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ahmed Salem Ould"))
                .andExpect(jsonPath("$.phone").value("+22246000004"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void listCanBeSearchedAndFiltered() throws Exception {
        String marker = "Zz" + randomDigits(6);
        mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"MORALE","email":"%s","details":{"displayName":"Société %s","nif":"%s","phone":"22000005"}}"""
                .formatted(uniqueEmail(), marker, randomDigits(8)))).andExpect(status().isCreated());

        mvc.perform(get("/api/admin/abonnes").param("q", marker.toLowerCase()).param("type", "MORALE").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Société " + marker));
        mvc.perform(get("/api/admin/abonnes").param("q", marker).param("statut", "ACTIVE").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/abonnes/resume").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PENDING").isNumber());
    }

    @Test
    void subscriberCannotManageSubscribers() throws Exception {
        mvc.perform(get("/api/admin/abonnes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ABONNE"))))
                .andExpect(status().isForbidden());
    }

    private ResultActions createPhysique(String email, String nni, String phone) throws Exception {
        return mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"PHYSIQUE","email":"%s","details":{"displayName":"Ahmed Salem","nni":"%s","phone":"%s"}}"""
                .formatted(email, nni, phone)));
    }

    private static String createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.subscriber.id");
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@epayment.mr"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String uniqueEmail() {
        return "abonne-" + UUID.randomUUID().toString().substring(0, 8) + "@exemple.mr";
    }

    private static String randomDigits(int length) {
        var random = ThreadLocalRandom.current();
        var digits = new StringBuilder();
        for (int i = 0; i < length; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }
}
