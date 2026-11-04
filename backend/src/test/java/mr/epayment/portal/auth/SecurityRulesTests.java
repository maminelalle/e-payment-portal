package mr.epayment.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import mr.epayment.portal.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Règles d'accès par rôle, vérifiées sur la vraie chaîne de sécurité et une vraie base PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityRulesTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anonymousCallerIsRejected() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void subscriberCannotCallAdminApiAndAttemptIsAudited() throws Exception {
        String subject = UUID.randomUUID().toString();

        mvc.perform(get("/api/admin/audit").with(user(subject, "abonne@exemple.mr", Roles.ABONNE)))
                .andExpect(status().isForbidden());

        assertThat(auditActions(subject)).containsExactly(SecurityConfig.ACCESS_DENIED);
    }

    @Test
    void adminCanReadAuditLog() throws Exception {
        var admin = user(UUID.randomUUID().toString(), "admin@epayment.mr", Roles.ADMIN);
        mvc.perform(get("/api/admin/audit").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // Un refus d'accès tracé, retrouvé par action, résultat, période et acteur.
        String subject = UUID.randomUUID().toString();
        mvc.perform(get("/api/admin/abonnes").with(user(subject, "curieux@exemple.mr", Roles.ABONNE)));
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nouakchott")).toString();
        mvc.perform(get("/api/admin/audit").with(admin)
                        .param("action", SecurityConfig.ACCESS_DENIED).param("resultat", "FAILURE")
                        .param("du", today).param("au", today).param("acteur", "curieux@exemple"))
                .andExpect(jsonPath("$.content[0].actorSubject").value(subject))
                .andExpect(jsonPath("$.content[0].resourceId").value("/api/admin/abonnes"));
        mvc.perform(get("/api/admin/audit").with(admin).param("ressource", "/api/admin/abonnes")
                        .param("du", "2020-01-01").param("au", "2020-01-31"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/audit/actions").with(admin))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem(SecurityConfig.ACCESS_DENIED)));
    }

    @Test
    void adminCannotCallSubscriberApi() throws Exception {
        mvc.perform(get("/api/abonne/contrat").with(user(UUID.randomUUID().toString(), "admin@epayment.mr", Roles.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutPortalRoleCannotCallEitherSpace() throws Exception {
        var noRole = user(UUID.randomUUID().toString(), "inconnu@exemple.mr");
        mvc.perform(get("/api/admin/audit").with(noRole)).andExpect(status().isForbidden());
        mvc.perform(get("/api/abonne/contrat").with(noRole)).andExpect(status().isForbidden());
    }

    @Test
    void unknownApiPathIsDenied() throws Exception {
        mvc.perform(get("/api/inconnu").with(user(UUID.randomUUID().toString(), "admin@epayment.mr", Roles.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginCreatesLocalAccountOnceAndIsAudited() throws Exception {
        String subject = UUID.randomUUID().toString();
        var subscriber = user(subject, "societe.abc@exemple.mr", Roles.ABONNE);

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/me/connexion").with(subscriber))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("societe.abc@exemple.mr"))
                    .andExpect(jsonPath("$.roles[0]").value(Roles.ABONNE))
                    .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());
        }

        assertThat(jdbc.queryForObject("select count(*) from app_user where keycloak_id = ?", Integer.class, subject))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("""
                select r.code from user_role ur
                join role r on r.id = ur.role_id
                join app_user u on u.id = ur.user_id
                where u.keycloak_id = ?""", String.class, subject)).containsExactly(Roles.ABONNE);
        assertThat(auditActions(subject)).containsExactly(UserAccountService.LOGIN, UserAccountService.LOGIN);

        mvc.perform(get("/api/me").with(subscriber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void sameVerifiedEmailWithNewKeycloakIdKeepsTheLocalAccount() throws Exception {
        String email = "relink-" + UUID.randomUUID() + "@exemple.mr";
        String before = UUID.randomUUID().toString();
        mvc.perform(post("/api/me/connexion").with(user(before, email, Roles.ABONNE))).andExpect(status().isOk());
        String id = jdbc.queryForObject("select id::text from app_user where keycloak_id = ?", String.class, before);

        // Realm réimporté : même personne, nouvel identifiant Keycloak → plus de 409, même compte local.
        String after = UUID.randomUUID().toString();
        mvc.perform(post("/api/me/connexion").with(user(after, email, Roles.ABONNE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("select keycloak_id from app_user where id = ?::uuid", String.class, id))
                .isEqualTo(after);
        assertThat(auditActions(after)).containsExactly(UserAccountService.ACCOUNT_RELINKED, UserAccountService.LOGIN);

        // Email non vérifié : pas de rattachement, le doublon reste refusé.
        mvc.perform(post("/api/me/connexion").with(user(UUID.randomUUID().toString(), email, false, Roles.ABONNE)))
                .andExpect(status().isConflict());
    }

    private List<String> auditActions(String subject) {
        return jdbc.queryForList("select action from audit_log where actor_subject = ? order by id", String.class,
                subject);
    }

    private static RequestPostProcessor user(String subject, String email, String... roles) {
        return user(subject, email, true, roles);
    }

    private static RequestPostProcessor user(String subject, String email, boolean emailVerified, String... roles) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .claim("email", email)
                        .claim("email_verified", emailVerified)
                        .claim("name", email)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }
}
