package mr.epayment.portal.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import mr.epayment.portal.gateway.PaymentProvider.Outcome;
import mr.epayment.portal.gateway.PaymentProvider.PaymentRequest;
import mr.epayment.portal.gateway.PaymentProvider.ProviderUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Adaptateurs HTTP face à de faux fournisseurs (WireMock) : format exact des requêtes et des réponses. */
class HttpPaymentProvidersTests {

    private WireMockServer server;
    private RestClient http;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        http = RestClient.builder().baseUrl(server.baseUrl()).defaultHeader("Authorization", "Bearer cle-test").build();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void bankilySendsIdempotencyKeyAndReadsItsStatuses() {
        server.stubFor(post("/payments").willReturn(aResponse().withStatus(202).withHeader("Content-Type",
                "application/json").withBody("{\"paymentId\":\"BKL-1\",\"status\":\"PENDING\"}")));
        server.stubFor(get("/payments/BKL-1").willReturn(okJson(
                "{\"paymentId\":\"BKL-1\",\"status\":\"FAILED\",\"failureCode\":\"INSUFFICIENT_FUNDS\"}")));
        var bankily = new HttpPaymentProviders.Bankily(http);

        var started = bankily.initiate(request("TRX-000001", "+22222000001"));
        assertThat(started.outcome()).isEqualTo(Outcome.PENDING);
        assertThat(started.providerReference()).isEqualTo("BKL-1");
        server.verify(postRequestedFor(urlEqualTo("/payments"))
                .withHeader("Authorization", equalTo("Bearer cle-test"))
                .withHeader("Idempotency-Key", equalTo("TRX-000001"))
                .withRequestBody(equalToJson("""
                        {"merchantReference":"TRX-000001","amount":12500.00,"currency":"MRU",
                         "payerPhone":"+22222000001","description":"Facture"}""")));

        var status = bankily.fetchStatus("BKL-1");
        assertThat(status.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(status.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void masrviUsesItsFrenchFormat() {
        server.stubFor(post("/transactions").willReturn(aResponse().withStatus(201).withHeader("Content-Type",
                "application/json").withBody("{\"idTransaction\":\"MSV-9\",\"etat\":\"ECHEC\",\"code\":\"51\"}")));

        var result = new HttpPaymentProviders.Masrvi(http).initiate(request("TRX-000002", "+22246000004"));

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.providerStatus()).isEqualTo("ECHEC/51");
        server.verify(postRequestedFor(urlEqualTo("/transactions")).withRequestBody(equalToJson("""
                {"reference":"TRX-000002","montant":"12500.00","devise":"MRU","telephoneClient":"22246000004",
                 "motif":"Facture"}""")));
    }

    @Test
    void visaCreatesAHostedSessionWithoutAnyCardData() {
        server.stubFor(post("/checkout-sessions").willReturn(aResponse().withStatus(201).withHeader("Content-Type",
                "application/json").withBody("""
                        {"sessionId":"cs_1","redirectUrl":"http://passerelle/pay/cs_1","status":"OPEN"}""")));

        var result = new HttpPaymentProviders.Visa(http).initiate(request("TRX-000003", null));

        assertThat(result.outcome()).isEqualTo(Outcome.PENDING);
        assertThat(result.redirectUrl()).isEqualTo("http://passerelle/pay/cs_1");
        var sent = server.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        assertThat(sent).contains("\"returnUrl\":\"http://localhost/abonne/paiement/retour?ref=TRX-000003\"")
                .doesNotContainIgnoringCase("card").doesNotContainIgnoringCase("cvc").doesNotContainIgnoringCase("pan");
    }

    @Test
    void providerErrorBecomesProviderUnavailable() {
        server.stubFor(post("/payments").willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> new HttpPaymentProviders.Bankily(http).initiate(request("TRX-000004", "+22222000001")))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    private static PaymentRequest request(String reference, String phone) {
        String back = "http://localhost/abonne/paiement/retour?ref=" + reference;
        return new PaymentRequest(reference, new BigDecimal("12500.00"), "MRU", phone, "Facture", back,
                back + "&annule=1");
    }
}
