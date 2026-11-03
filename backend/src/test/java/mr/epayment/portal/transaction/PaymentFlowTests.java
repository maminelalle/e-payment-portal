package mr.epayment.portal.transaction;

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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

/** Transactions : idempotence, plafond mensuel, mise à jour par webhook, cloisonnement entre abonnés. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentFlowTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private OtpSender otpSender;

    private String subject;

    @BeforeEach
    void subscriberWithActiveContract() throws Exception {
        subject = UUID.randomUUID().toString();
        when(keycloak.createUser(anyString(), anyString())).thenReturn(subject);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        activeContract(subject, 50_000, 60_000);
    }

    @Test
    void sameIdempotencyKeyNeverPaysTwice() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = JsonPath.read(pay(subject, "BANKILY", "1000.00", key).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("TRX-\\d{6}")))
                .andReturn().getResponse().getContentAsString(), "$.reference");

        pay(subject, "BANKILY", "1000.00", key).andExpect(jsonPath("$.reference").value(first));
        assertThat(jdbc.queryForObject("select count(*) from payment_transaction where idempotency_key = ?",
                Integer.class, key)).isEqualTo(1);

        mvc.perform(post("/api/abonne/paiements").with(subscriber(subject)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"BANKILY\",\"amount\":10,\"payerPhone\":\"22000001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.idempotencyKey").exists());
    }

    @Test
    void monthlyLimitCountsWhatWasAlreadyPaid() throws Exception {
        pay(subject, "BANKILY", "40000.00", UUID.randomUUID().toString()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        // 40 000 + 30 000 > 60 000 de plafond mensuel
        pay(subject, "BANKILY", "30000.00", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.refusal").value("OVER_MONTHLY_LIMIT"));
        // Un paiement échoué ne compte pas
        pay(subject, "BANKILY", "15000.51", UUID.randomUUID().toString()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureMessage").value("Solde insuffisant."));
        pay(subject, "BANKILY", "20000.00", UUID.randomUUID().toString()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void signedWebhookCompletesAPendingPaymentOnce() throws Exception {
        String reference = JsonPath.read(pay(subject, "BANKILY", "1200.99", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "$.reference");

        // Montant incohérent : ignoré et tracé
        webhook(reference, "999.00", "SUCCESS").andExpect(status().isOk());
        mvc.perform(get("/api/abonne/paiements/{ref}", reference).with(subscriber(subject)))
                .andExpect(jsonPath("$.status").value("PENDING"));

        webhook(reference, "1200.99", "SUCCESS").andExpect(status().isOk());
        mvc.perform(get("/api/abonne/paiements/{ref}", reference).with(subscriber(subject)))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // Un message tardif contradictoire ne change plus une transaction terminée
        webhook(reference, "1200.99", "FAILED").andExpect(status().isOk());
        mvc.perform(get("/api/abonne/paiements/{ref}", reference).with(subscriber(subject)))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jdbc.queryForList("select action from audit_log where resource_id = ? order by id", String.class,
                reference)).contains("PAYMENT_INITIATED", "WEBHOOK_ANOMALY", "PAYMENT_UPDATED");
    }

    @Test
    void subscribersOnlySeeTheirOwnTransactions() throws Exception {
        String reference = JsonPath.read(pay(subject, "BANKILY", "500.00", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString(), "$.reference");
        mvc.perform(get("/api/abonne/paiements").with(subscriber(subject)))
                .andExpect(jsonPath("$.content[0].reference").value(reference));

        String other = UUID.randomUUID().toString();
        when(keycloak.createUser(anyString(), anyString())).thenReturn(other);
        activeContract(other, 50_000, 500_000);
        mvc.perform(get("/api/abonne/paiements/{ref}", reference).with(subscriber(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/abonne/paiements").with(subscriber(other)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private ResultActions pay(String who, String method, String amount, String key) throws Exception {
        return mvc.perform(post("/api/abonne/paiements").with(subscriber(who)).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content("{\"method\":\"%s\",\"amount\":%s,\"payerPhone\":\"22000001\"}".formatted(method, amount)));
    }

    private ResultActions webhook(String reference, String amount, String status) throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String body = """
                {"eventId":"%s","type":"payment","paymentId":"BKL-x","merchantReference":"%s","status":"%s",
                 "amount":%s,"currency":"MRU","failureCode":null,"occurredAt":"2026-11-03T12:00:00Z"}"""
                .formatted(eventId, reference, status, amount);
        long ts = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("bankily-dev-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "v1=" + HexFormat.of().formatHex(mac.doFinal((ts + "." + body).getBytes(StandardCharsets.UTF_8)));
        return mvc.perform(post("/api/webhooks/bankily").contentType(MediaType.APPLICATION_JSON)
                .header("X-Event-Id", eventId).header("X-Timestamp", ts).header("X-Signature", signature).content(body));
    }

    /** Abonné créé, contrat Bankily envoyé, signé (code simulé) : activé automatiquement. */
    private void activeContract(String who, int perTransaction, int monthly) throws Exception {
        String nif = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        String subscriberId = JsonPath.read(mvc.perform(post("/api/admin/abonnes").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"MORALE","email":"p%s@exemple.mr","details":{"displayName":"Société Paiement","nif":"%s","phone":"22000001"}}"""
                        .formatted(UUID.randomUUID().toString().substring(0, 8), nif)))
                .andReturn().getResponse().getContentAsString(), "$.subscriber.id");
        String templateId = JsonPath.read(mvc.perform(get("/api/admin/contrats/modeles").with(admin()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        String contractId = JsonPath.read(mvc.perform(post("/api/admin/contrats").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscriberId\":\"%s\",\"templateId\":\"%s\"}".formatted(subscriberId, templateId)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/admin/contrats/{id}", contractId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"templateId":"%s","startDate":"%s","endDate":"%s","methods":[
                        {"code":"BANKILY","perTransactionLimit":%d,"monthlyLimit":%d}]}"""
                        .formatted(templateId, LocalDate.now(), LocalDate.now().plusYears(1), perTransaction, monthly)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/contrats/{id}/envoyer", contractId).with(admin())).andExpect(status().isOk());
        String challenge = mvc.perform(post("/api/abonne/contrat/signature").with(subscriber(who)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var captor = ArgumentCaptor.forClass(OtpMessage.class);
        verify(otpSender, atLeastOnce()).send(captor.capture());
        mvc.perform(post("/api/abonne/contrat/signature/{id}/confirmation", JsonPath.<String>read(challenge, "$.signatureId"))
                        .with(subscriber(who)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + captor.getValue().code() + "\"}"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor subscriber(String who) {
        return jwt().jwt(token -> token.subject(who)).authorities(new SimpleGrantedAuthority("ROLE_ABONNE"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@epayment.mr"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
