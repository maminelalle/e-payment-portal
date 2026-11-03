package mr.epayment.portal.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import mr.epayment.portal.TestcontainersConfiguration;
import mr.epayment.portal.gateway.PaymentProvider.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Webhooks des fournisseurs : sans jeton, mais signés, récents et jamais traités deux fois. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class WebhookTests {

    private static final String BANKILY_SECRET = "bankily-dev-webhook-secret";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @Test
    void validBankilyWebhookIsRecordedOnceAndPublished() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String body = bankilyBody(eventId, "TRX-9001", "SUCCESS", null);

        send("bankily", BANKILY_SECRET, eventId, body, now()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
        // Rejeu : même événement renvoyé (réessai du fournisseur ou attaque) → ignoré.
        send("bankily", BANKILY_SECRET, eventId, body, now()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(jdbc.queryForObject("select count(*) from webhook_event where event_id = ?", Integer.class, eventId))
                .isEqualTo(1);
        var published = events.stream(ProviderWebhook.class).filter(w -> w.eventId().equals(eventId)).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(published.getFirst().merchantReference()).isEqualTo("TRX-9001");
        assertThat(published.getFirst().amount()).isEqualByComparingTo("12500");
    }

    @Test
    void reusedEventIdWithDifferentContentIsFlaggedNotApplied() throws Exception {
        String eventId = "ntf-" + UUID.randomUUID();
        String first = """
                {"evenementId":"%s","idTransaction":"MSV-1","reference":"TRX-9101","etat":"ECHEC","code":"05",
                 "montant":"10.00","dateHeure":"2026-11-03T10:00:00Z"}""".formatted(eventId);
        String other = first.replace("TRX-9101", "TRX-9102").replace("ECHEC", "SUCCES").replace("\"05\"", "\"00\"");

        send("masrvi", "masrvi-dev-webhook-secret", eventId, first, now()).andExpect(status().isOk());
        send("masrvi", "masrvi-dev-webhook-secret", eventId, first, now())
                .andExpect(jsonPath("$.status").value("duplicate"));
        send("masrvi", "masrvi-dev-webhook-secret", eventId, other, now())
                .andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'WEBHOOK_EVENT_ID_REUSED' "
                + "and resource_id = ?", Integer.class, eventId)).isEqualTo(1);
        assertThat(events.stream(ProviderWebhook.class).filter(w -> w.eventId().equals(eventId)).count()).isEqualTo(1);
    }

    @Test
    void wrongSignatureOrStaleTimestampIsRejected() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        String body = bankilyBody(eventId, "TRX-9002", "SUCCESS", null);

        send("bankily", "mauvais-secret", eventId, body, now()).andExpect(status().isUnauthorized());
        send("bankily", BANKILY_SECRET, eventId, body, now() - 600).andExpect(status().isUnauthorized());
        // Corps modifié après signature
        long ts = now();
        mvc.perform(post("/api/webhooks/bankily").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Event-Id", eventId).header("X-Timestamp", ts)
                        .header("X-Signature", "v1=" + WebhookVerifier.hmac(BANKILY_SECRET, ts + "." + body))
                        .content(body.replace("12500", "1")))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from webhook_event where event_id = ?", Integer.class, eventId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'WEBHOOK_REJECTED' "
                + "and resource_id = ?", Integer.class, eventId)).isEqualTo(3);
    }

    @Test
    void masrviFormatIsTranslated() throws Exception {
        String eventId = "ntf-" + UUID.randomUUID();
        String body = """
                {"evenementId":"%s","idTransaction":"MSV-42","reference":"TRX-9003","etat":"ECHEC","code":"51",
                 "montant":"3750.51","dateHeure":"2026-11-03T10:15:41Z"}""".formatted(eventId);

        send("masrvi", "masrvi-dev-webhook-secret", eventId, body, now()).andExpect(status().isOk());

        ProviderWebhook webhook = published(eventId);
        assertThat(webhook.providerCode()).isEqualTo("MASRVI");
        assertThat(webhook.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(webhook.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(webhook.amount()).isEqualByComparingTo(new BigDecimal("3750.51"));
        assertThat(webhook.providerStatus()).isEqualTo("ECHEC/51");
    }

    @Test
    void visaFormatKeepsOnlyTheMaskedCard() throws Exception {
        String eventId = "evt_v_" + UUID.randomUUID();
        String body = """
                {"id":"%s","type":"checkout.session.completed","sessionId":"cs_1","orderReference":"TRX-9004",
                 "status":"AUTHORIZED","amount":48000,"currency":"MRU",
                 "card":{"brand":"VISA","maskedPan":"411111******1111","token":"tok_abc"},"created":1793616000}"""
                .formatted(eventId);

        send("visa", "visa-dev-webhook-secret", eventId, body, now()).andExpect(status().isOk());

        ProviderWebhook webhook = published(eventId);
        assertThat(webhook.outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(webhook.maskedPan()).isEqualTo("411111******1111");
        assertThat(webhook.cardToken()).isEqualTo("tok_abc");
    }

    @Test
    void malformedOrInconsistentWebhooksAreRefused() throws Exception {
        String eventId = "evt_" + UUID.randomUUID();
        send("bankily", BANKILY_SECRET, "autre-id", bankilyBody(eventId, "TRX-9005", "SUCCESS", null), now())
                .andExpect(status().isBadRequest());
        send("bankily", BANKILY_SECRET, eventId, "{pas du json", now()).andExpect(status().isBadRequest());
        send("inconnu", BANKILY_SECRET, eventId, "{}", now()).andExpect(status().isNotFound());
    }

    private ProviderWebhook published(String eventId) {
        return events.stream(ProviderWebhook.class).filter(w -> w.eventId().equals(eventId)).findFirst().orElseThrow();
    }

    private ResultActions send(String provider, String secret, String eventId, String body, long timestamp)
            throws Exception {
        return mvc.perform(post("/api/webhooks/{provider}", provider).contentType(MediaType.APPLICATION_JSON)
                .header("X-Event-Id", eventId)
                .header("X-Timestamp", timestamp)
                .header("X-Signature", "v1=" + WebhookVerifier.hmac(secret, timestamp + "." + body))
                .content(body));
    }

    private static String bankilyBody(String eventId, String reference, String status, String failureCode) {
        return """
                {"eventId":"%s","type":"payment.succeeded","paymentId":"BKL-1","merchantReference":"%s",
                 "status":"%s","amount":12500.00,"currency":"MRU","failureCode":%s,"occurredAt":"2026-11-03T11:42:09Z"}"""
                .formatted(eventId, reference, status, failureCode == null ? "null" : "\"" + failureCode + "\"");
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
