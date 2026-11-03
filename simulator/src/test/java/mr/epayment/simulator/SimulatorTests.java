package mr.epayment.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Le simulateur face à un faux portail qui reçoit les webhooks (serveur HTTP local). */
@SpringBootTest(properties = {
        "simulator.confirm-delay=200ms",
        "simulator.delayed-confirm-delay=1s",
        "simulator.webhook-retry-delays=200ms,200ms,200ms"})
@AutoConfigureMockMvc
class SimulatorTests {

    private static final BlockingQueue<Received> WEBHOOKS = new LinkedBlockingQueue<>();
    private static final AtomicInteger FAILURES_TO_SIMULATE = new AtomicInteger();
    private static final HttpServer PORTAL = startFakePortal();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void portal(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + PORTAL.getAddress().getPort() + "/api/webhooks/";
        registry.add("simulator.providers.bankily.webhook-url", () -> base + "bankily");
        registry.add("simulator.providers.masrvi.webhook-url", () -> base + "masrvi");
        registry.add("simulator.providers.visa.webhook-url", () -> base + "visa");
    }

    @BeforeEach
    void reset() {
        WEBHOOKS.clear();
        FAILURES_TO_SIMULATE.set(0);
    }

    @AfterAll
    static void stop() {
        PORTAL.stop(0);
    }

    @Test
    void bankilyPaymentSucceedsAndSendsSignedWebhook() throws Exception {
        String body = bankily("TRX-1", "12500.00", "key-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.paymentId");

        Received webhook = nextWebhook("TRX-1");
        assertThat(webhook.path()).isEqualTo("/api/webhooks/bankily");
        assertThat(webhook.headers().get("X-Signature"))
                .isEqualTo(WebhookSender.sign("bankily-dev-webhook-secret",
                        Long.parseLong(webhook.headers().get("X-Timestamp")), webhook.body()));
        assertThat(webhook.headers().get("X-Event-Id")).isEqualTo(JsonPath.read(webhook.body(), "$.eventId"));
        assertThat(JsonPath.<String>read(webhook.body(), "$.type")).isEqualTo("payment.succeeded");
        assertThat(JsonPath.<String>read(webhook.body(), "$.merchantReference")).isEqualTo("TRX-1");

        mvc.perform(get("/bankily/api/v1/payments/{id}", paymentId).header("Authorization", "Bearer bankily-dev-api-key"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void idempotencyKeyNeverCreatesASecondPayment() throws Exception {
        String first = JsonPath.read(bankily("TRX-2", "100.00", "key-2").andReturn().getResponse().getContentAsString(),
                "$.paymentId");
        String again = JsonPath.read(bankily("TRX-2", "100.00", "key-2").andReturn().getResponse().getContentAsString(),
                "$.paymentId");
        assertThat(again).isEqualTo(first);
        bankily("TRX-2", "999.00", "key-2").andExpect(status().isConflict());
    }

    @Test
    void wrongApiKeyIsRejected() throws Exception {
        mvc.perform(post("/bankily/api/v1/payments").header("Authorization", "Bearer mauvaise-cle")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void masrviInsufficientFundsUsesItsOwnFormat() throws Exception {
        mvc.perform(post("/masrvi/v2/transactions").header("Authorization", "Bearer masrvi-dev-api-key")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reference":"TRX-3","montant":"3750.51","devise":"MRU","telephoneClient":"22246000004"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("EN_COURS"));

        Received webhook = nextWebhook("TRX-3");
        assertThat(JsonPath.<String>read(webhook.body(), "$.etat")).isEqualTo("ECHEC");
        assertThat(JsonPath.<String>read(webhook.body(), "$.code")).isEqualTo("51");
        assertThat(JsonPath.<String>read(webhook.body(), "$.montant")).isEqualTo("3750.51");
        assertThat(webhook.headers().get("X-Event-Id")).isEqualTo(JsonPath.read(webhook.body(), "$.evenementId"));
    }

    @Test
    void refusedWebhookIsRetriedWithTheSameEventId() throws Exception {
        FAILURES_TO_SIMULATE.set(1);
        bankily("TRX-4", "100.00", null);

        Received refused = nextWebhook("TRX-4");
        Received retried = nextWebhook("TRX-4");
        assertThat(retried.headers().get("X-Event-Id")).isEqualTo(refused.headers().get("X-Event-Id"));
        assertThat(retried.body()).isEqualTo(refused.body());
    }

    @Test
    void noWebhookScenarioOnlyShowsInStatus() throws Exception {
        String paymentId = JsonPath.read(bankily("TRX-5", "100.98", null).andReturn().getResponse()
                .getContentAsString(), "$.paymentId");
        assertThat(pollFor("TRX-5", 1)).isNull();
        mvc.perform(get("/bankily/api/v1/payments/{id}", paymentId).header("Authorization", "Bearer bankily-dev-api-key"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void visaHostedPageKeepsCardDataAwayFromThePortal() throws Exception {
        String session = mvc.perform(post("/visa-gateway/api/v1/checkout-sessions")
                        .header("Authorization", "Bearer visa-dev-api-key").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderReference":"TRX-6","amount":48000,"currency":"MRU",
                                 "returnUrl":"http://localhost/abonne/paiement/TRX-6","cancelUrl":"http://localhost/abonne"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(session, "$.sessionId");
        assertThat(JsonPath.<String>read(session, "$.redirectUrl")).endsWith("/visa-gateway/pay/" + sessionId);

        mvc.perform(get("/visa-gateway/pay/{id}", sessionId)).andExpect(status().isOk());
        mvc.perform(post("/visa-gateway/pay/{id}", sessionId).param("cardNumber", "4111 1111 1111 1111")
                        .param("expiry", "12/28").param("cvc", "123"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost/abonne/paiement/TRX-6?sessionId=" + sessionId));

        Received webhook = nextWebhook("TRX-6");
        assertThat(JsonPath.<String>read(webhook.body(), "$.type")).isEqualTo("checkout.session.completed");
        assertThat(JsonPath.<String>read(webhook.body(), "$.card.maskedPan")).isEqualTo("411111******1111");
        assertThat(webhook.body()).doesNotContain("4111111111111111").doesNotContain("\"cvc\"");
    }

    private ResultActions bankily(String reference, String amount, String idempotencyKey) throws Exception {
        var request = post("/bankily/api/v1/payments").header("Authorization", "Bearer bankily-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"merchantReference":"%s","amount":%s,"currency":"MRU","payerPhone":"+22222000001"}"""
                        .formatted(reference, amount));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request);
    }

    /** Prochain webhook concernant cette commande (ceux des tests précédents peuvent encore arriver). */
    private static Received nextWebhook(String reference) throws InterruptedException {
        Received received = pollFor(reference, 5);
        assertThat(received).as("webhook reçu par le portail pour " + reference).isNotNull();
        return received;
    }

    private static Received pollFor(String reference, int seconds) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            Received received = WEBHOOKS.poll(100, TimeUnit.MILLISECONDS);
            if (received != null && received.body().contains("\"" + reference + "\"")) {
                return received;
            }
        }
        return null;
    }

    private static HttpServer startFakePortal() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/webhooks/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var headers = exchange.getRequestHeaders();
                WEBHOOKS.add(new Received(exchange.getRequestURI().getPath(), Map.of(
                        "X-Signature", headers.getFirst("X-Signature"),
                        "X-Timestamp", headers.getFirst("X-Timestamp"),
                        "X-Event-Id", headers.getFirst("X-Event-Id")), body));
                int status = FAILURES_TO_SIMULATE.getAndUpdate(n -> Math.max(0, n - 1)) > 0 ? 500 : 200;
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    record Received(String path, Map<String, String> headers, String body) {
    }
}
