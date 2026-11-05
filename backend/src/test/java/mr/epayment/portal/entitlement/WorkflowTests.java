package mr.epayment.portal.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mr.epayment.portal.TestcontainersConfiguration;
import mr.epayment.portal.auth.KeycloakAdminClient;
import mr.epayment.portal.signature.OtpSender;
import mr.epayment.portal.signature.OtpSender.OtpMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Jalon de la semaine 7 : le workflow complet, de la création de l'abonné aux paiements, puis la fermeture
 * des droits (suspension, expiration, résiliation). Base PostgreSQL et stockage S3 réels ; Keycloak, email et
 * envoi du code simulés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkflowTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntitlementJobs jobs;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private OtpSender otpSender;

    private String subscriberSubject;

    @BeforeEach
    void setUp() {
        subscriberSubject = UUID.randomUUID().toString();
        when(keycloak.createUser(anyString(), anyString())).thenReturn(subscriberSubject);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
    }

    @Test
    void fromSubscriberCreationToPaymentsThenRevocation() throws Exception {
        // 1-4. Création, contrat Bankily + Visa, envoi, signature
        String subscriberId = createSubscriber();
        String contractId = sendContract(subscriberId, LocalDate.now(), LocalDate.now().plusYears(1));
        mvc.perform(get("/api/abonne/moyens").with(subscriber())).andExpect(jsonPath("$.length()").value(0));
        sign();

        // 5. Activation automatique après la signature : contrat actif, abonné actif, un droit par moyen
        mvc.perform(get("/api/admin/contrats/{id}", contractId).with(admin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty());
        mvc.perform(get("/api/admin/abonnes/{id}", subscriberId).with(admin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/admin/contrats/{id}/droits", contractId).with(admin()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ACTIVE"))));

        // Fiche abonné côté admin : les mêmes moyens autorisés (conception §3.2)
        mvc.perform(get("/api/admin/abonnes/{id}/moyens", subscriberId).with(admin()))
                .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.containsInAnyOrder("BANKILY", "VISA")));

        // 6. Espace abonné : uniquement les moyens du contrat
        mvc.perform(get("/api/abonne/moyens").with(subscriber()))
                .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.containsInAnyOrder("BANKILY", "VISA")))
                .andExpect(jsonPath("$[0].available").value(true));

        // 7. Paiements : contrôle serveur à chaque appel
        pay("BANKILY", "12500.00", "22 00 00 01").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.contractId").value(contractId));
        pay("VISA", "48000.00", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        pay("BANKILY", "3750.51", "22000001").andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"));
        pay("MASRVI", "1000.00", "22000001").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.refusal").value("NOT_ENTITLED"));
        pay("BANKILY", "50000.01", "22000001").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.refusal").value("OVER_TRANSACTION_LIMIT"));
        pay("BANKILY", "1000.00", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.payerPhone").exists());

        // 8. Idempotence : rejouer l'activation ne crée aucun doublon
        mvc.perform(post("/api/admin/contrats/{id}/activer", contractId).with(admin())).andExpect(status().isOk());
        assertThat(entitlementCount(contractId)).isEqualTo(2);

        // 9. Suspension → droits suspendus, paiement refusé ; reprise → droits rouverts
        mvc.perform(post("/api/admin/contrats/{id}/suspendre", contractId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Contrôle\"}"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/abonne/moyens").with(subscriber())).andExpect(jsonPath("$.length()").value(0));
        pay("VISA", "100.00", null).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/contrats/{id}/reprendre", contractId).with(admin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        pay("VISA", "100.00", null).andExpect(status().isOk());

        // 10. Abonné suspendu : ses paiements sont bloqués même avec des droits actifs
        mvc.perform(post("/api/admin/abonnes/{id}/suspendre", subscriberId).with(admin())).andExpect(status().isOk());
        pay("VISA", "100.00", null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.refusal").value("SUBSCRIBER_BLOCKED"));
        mvc.perform(post("/api/admin/abonnes/{id}/activer", subscriberId).with(admin())).andExpect(status().isOk());

        // 11. Résiliation → droits révoqués, historique conservé
        mvc.perform(post("/api/admin/contrats/{id}/resilier", contractId).with(admin()))
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.terminatedAt").isNotEmpty());
        mvc.perform(get("/api/admin/contrats/{id}/droits", contractId).with(admin()))
                .andExpect(jsonPath("$[*].status", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("REVOKED"))));
        pay("BANKILY", "100.00", "22000001").andExpect(status().isForbidden());
        assertThat(entitlementCount(contractId)).isEqualTo(2);
    }

    @Test
    void dailyJobExpiresEndedContractsAndActivatesMissedOnes() throws Exception {
        String subscriberId = createSubscriber();
        String contractId = sendContract(subscriberId, LocalDate.now().minusDays(10), LocalDate.now().plusDays(10));
        sign();
        assertThat(contractStatus(contractId)).isEqualTo("ACTIVE");

        // Rattrapage : un contrat resté « signé » est activé par la tâche quotidienne.
        jdbc.update("update contract set status = 'SIGNED' where id = ?::uuid", contractId);
        jdbc.update("delete from subscriber_entitlement where contract_id = ?::uuid", contractId);
        var catchUp = jobs.runDaily(LocalDate.now());
        assertThat(catchUp.activated()).isGreaterThanOrEqualTo(1);
        assertThat(contractStatus(contractId)).isEqualTo("ACTIVE");
        assertThat(entitlementCount(contractId)).isEqualTo(2);

        // Échéance : le lendemain de la date de fin, contrat et droits expirent.
        jobs.runDaily(LocalDate.now().plusDays(11));
        assertThat(contractStatus(contractId)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForList("select status from subscriber_entitlement where contract_id = ?::uuid",
                String.class, contractId)).containsOnly("EXPIRED");
        mvc.perform(get("/api/abonne/moyens").with(subscriber())).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void contractNotYetInForceGivesNoPayment() throws Exception {
        String subscriberId = createSubscriber();
        sendContract(subscriberId, LocalDate.now().plusDays(5), LocalDate.now().plusYears(1), "BANKILY");
        sign();

        mvc.perform(get("/api/abonne/moyens").with(subscriber()))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].unavailableReason").value("Contrat pas encore en vigueur"));
        pay("BANKILY", "100.00", "22000001").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.refusal").value("OUTSIDE_VALIDITY"));
    }

    @Test
    void amendmentReplacesTheContractInForceAndKeepsItsHistory() throws Exception {
        String subscriberId = createSubscriber();
        String contractId = sendContract(subscriberId, LocalDate.now(), LocalDate.now().plusYears(1));
        sign();
        String reference = JsonPath.read(mvc.perform(get("/api/admin/contrats/{id}", contractId).with(admin()))
                .andReturn().getResponse().getContentAsString(), "$.reference");
        pay("BANKILY", "12500.00", "22000001").andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // 1. Avenant : brouillon prérempli, numéroté, un seul à la fois
        String body = mvc.perform(post("/api/admin/contrats/{id}/avenant", contractId).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(reference + "-A1"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.amendment.number").value(1))
                .andExpect(jsonPath("$.amendment.replacesReference").value(reference))
                .andExpect(jsonPath("$.methods.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String amendmentId = JsonPath.read(body, "$.id");
        String templateId = JsonPath.read(body, "$.template.id");
        mvc.perform(post("/api/admin/contrats/{id}/avenant", contractId).with(admin()))
                .andExpect(status().isConflict());

        // 2. Nouveaux moyens : Bankily (plafond mensuel réduit) et Masrvi, plus de Visa ; envoi accepté
        //    alors que le contrat d'origine est encore en vigueur
        mvc.perform(put("/api/admin/contrats/{id}", amendmentId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"templateId":"%s","startDate":"%s","endDate":"%s","methods":[
                         {"code":"BANKILY","perTransactionLimit":15000,"monthlyLimit":20000},
                         {"code":"MASRVI","perTransactionLimit":50000,"monthlyLimit":500000}]}"""
                        .formatted(templateId, LocalDate.now(), LocalDate.now().plusYears(1))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/contrats/{id}/envoyer", amendmentId).with(admin())).andExpect(status().isOk());
        assertThat(contractStatus(contractId)).isEqualTo("ACTIVE");
        pay("VISA", "1000.00", null).andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // 3. Le PDF est celui d'un avenant et nomme le contrat remplacé
        byte[] pdf = mvc.perform(get("/api/admin/contrats/{id}/pdf", amendmentId).with(admin()))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            // L'extraction place le numéro en fin de ligne : on vérifie le titre et le contrat remplacé.
            assertThat(text).contains("AVENANT N°").contains("AU CONTRAT D'ABONNEMENT").contains("remplace")
                    .contains(reference);
        }

        // 4. Signature → l'avenant est actif, l'original remplacé, ses droits révoqués
        sign();
        assertThat(contractStatus(amendmentId)).isEqualTo("ACTIVE");
        assertThat(contractStatus(contractId)).isEqualTo("REPLACED");
        mvc.perform(get("/api/admin/contrats/{id}", contractId).with(admin()))
                .andExpect(jsonPath("$.terminationReason").value("Remplacé par l'avenant " + reference + "-A1"));
        mvc.perform(get("/api/admin/contrats/{id}/droits", contractId).with(admin()))
                .andExpect(jsonPath("$[*].status", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("REVOKED"))));
        mvc.perform(get("/api/abonne/moyens").with(subscriber()))
                .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.containsInAnyOrder("BANKILY", "MASRVI")));

        // 5. Paiements sous l'avenant : Visa n'est plus ouvert ; le plafond mensuel de Bankily compte le paiement
        //    déjà fait sous l'ancien contrat (12 500 + 10 000 > 20 000)
        pay("VISA", "1000.00", null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.refusal").value("NOT_ENTITLED"));
        pay("BANKILY", "10000.00", "22000001").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.refusal").value("OVER_MONTHLY_LIMIT"));
        pay("MASRVI", "1000.00", "22000001").andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(amendmentId));
        assertThat(jdbc.queryForObject("select count(*) from payment_transaction where contract_id = ?::uuid",
                Integer.class, contractId)).isEqualTo(2);

        // 6. Historique : l'initial puis l'avenant ; un contrat remplacé ne reçoit pas d'avenant
        mvc.perform(get("/api/admin/contrats/{id}/versions", amendmentId).with(admin()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reference").value(reference))
                .andExpect(jsonPath("$[0].status").value("REPLACED"))
                .andExpect(jsonPath("$[1].reference").value(reference + "-A1"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
        mvc.perform(post("/api/admin/contrats/{id}/avenant", contractId).with(admin()))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/admin/contrats/{id}/avenant", amendmentId).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(reference + "-A2"))
                .andExpect(jsonPath("$.amendment.replacesReference").value(reference + "-A1"));
    }

    @Test
    void amendmentOfASuspendedContractStaysSuspended() throws Exception {
        String subscriberId = createSubscriber();
        String contractId = sendContract(subscriberId, LocalDate.now(), LocalDate.now().plusYears(1));
        sign();
        String amendmentId = JsonPath.read(mvc.perform(post("/api/admin/contrats/{id}/avenant", contractId)
                .with(admin())).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/contrats/{id}/envoyer", amendmentId).with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/contrats/{id}/suspendre", contractId).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Contrôle\"}"))
                .andExpect(status().isOk());

        sign();
        assertThat(contractStatus(contractId)).isEqualTo("REPLACED");
        assertThat(contractStatus(amendmentId)).isEqualTo("SUSPENDED");
        pay("BANKILY", "100.00", "22000001").andExpect(status().isForbidden());
    }

    private void sign() throws Exception {
        String challenge = mvc.perform(post("/api/abonne/contrat/signature").with(subscriber()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var captor = ArgumentCaptor.forClass(OtpMessage.class);
        verify(otpSender, atLeastOnce()).send(captor.capture());
        mvc.perform(post("/api/abonne/contrat/signature/{id}/confirmation", JsonPath.<String>read(challenge, "$.signatureId"))
                        .with(subscriber()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + captor.getValue().code() + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions pay(String method, String amount, String phone) throws Exception {
        String phoneJson = phone == null ? "null" : "\"" + phone + "\"";
        return mvc.perform(post("/api/abonne/paiements").with(subscriber()).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("{\"method\":\"%s\",\"amount\":%s,\"payerPhone\":%s,\"description\":\"Facture\"}"
                        .formatted(method, amount, phoneJson)));
    }

    private String sendContract(String subscriberId, LocalDate start, LocalDate end, String... codes) throws Exception {
        String templateId = JsonPath.read(mvc.perform(get("/api/admin/contrats/modeles").with(admin()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        String contractId = JsonPath.read(mvc.perform(post("/api/admin/contrats").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscriberId\":\"%s\",\"templateId\":\"%s\"}".formatted(subscriberId, templateId)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String methods = codes.length == 0
                ? """
                  [{"code":"BANKILY","perTransactionLimit":50000,"monthlyLimit":500000},
                   {"code":"VISA","perTransactionLimit":100000,"monthlyLimit":1000000}]"""
                : "[{\"code\":\"%s\",\"perTransactionLimit\":50000,\"monthlyLimit\":500000}]".formatted(codes[0]);
        mvc.perform(put("/api/admin/contrats/{id}", contractId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"methods\":%s}"
                        .formatted(templateId, start, end, methods))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/contrats/{id}/envoyer", contractId).with(admin())).andExpect(status().isOk());
        return contractId;
    }

    private String createSubscriber() throws Exception {
        String nif = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        String body = mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MORALE","email":"w%s@exemple.mr","details":{"displayName":"Société Workflow","nif":"%s","phone":"22000001"}}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 8), nif)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.subscriber.id");
    }

    private String contractStatus(String contractId) {
        return jdbc.queryForObject("select status from contract where id = ?::uuid", String.class, contractId);
    }

    private int entitlementCount(String contractId) {
        return jdbc.queryForObject("select count(*) from subscriber_entitlement where contract_id = ?::uuid",
                Integer.class, contractId);
    }

    private RequestPostProcessor subscriber() {
        return jwt().jwt(token -> token.subject(subscriberSubject)).authorities(new SimpleGrantedAuthority("ROLE_ABONNE"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@epayment.mr"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
