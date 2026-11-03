package mr.epayment.portal.gateway;

import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import mr.epayment.portal.gateway.PaymentProvider.Outcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptateurs HTTP des fournisseurs (mode par défaut). En développement ils visent le simulateur ;
 * passer aux vraies API = changer {@code epayment.providers.*} (adresse, clé), pas le code.
 * Le portail n'envoie jamais de données de carte : pour Visa il crée une session, la carte est saisie
 * sur la page hébergée de la passerelle.
 */
@Configuration
@ConditionalOnProperty(name = "epayment.providers.mode", havingValue = "simulator", matchIfMissing = true)
@EnableConfigurationProperties(ProvidersProperties.class)
class HttpPaymentProviders {

    @Bean
    PaymentProvider bankilyProvider(RestClient.Builder builder, ProvidersProperties properties) {
        return new Bankily(client(builder, properties.bankily()));
    }

    @Bean
    PaymentProvider masrviProvider(RestClient.Builder builder, ProvidersProperties properties) {
        return new Masrvi(client(builder, properties.masrvi()));
    }

    @Bean
    PaymentProvider visaGatewayProvider(RestClient.Builder builder, ProvidersProperties properties) {
        return new Visa(client(builder, properties.visa()));
    }

    private static RestClient client(RestClient.Builder builder, ProvidersProperties.Provider provider) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return builder.clone()
                .baseUrl(provider.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + provider.apiKey())
                .build();
    }

    private static Map<?, ?> call(String provider, Supplier<Map<?, ?>> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new PaymentProvider.ProviderUnavailableException(provider + " ne répond pas ou refuse la demande.", e);
        }
    }

    private static String text(Map<?, ?> body, String field) {
        Object value = body.get(field);
        return value == null ? null : value.toString();
    }

    /** Bankily : REST anglais, statuts PENDING / SUCCESS / FAILED. */
    record Bankily(RestClient http) implements PaymentProvider {

        @Override
        public String code() {
            return "BANKILY";
        }

        @Override
        public ProviderResult initiate(PaymentRequest request) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("merchantReference", request.merchantReference());
            body.put("amount", request.amount());
            body.put("currency", request.currency());
            body.put("payerPhone", request.payerPhone());
            body.put("description", request.description());
            return result(call("Bankily", () -> http.post().uri("/payments")
                    .header("Idempotency-Key", request.merchantReference())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class)));
        }

        @Override
        public ProviderResult fetchStatus(String providerReference) {
            return result(call("Bankily", () -> http.get().uri("/payments/{id}", providerReference)
                    .retrieve().body(Map.class)));
        }

        static Outcome outcome(String status) {
            return switch (String.valueOf(status)) {
                case "SUCCESS" -> Outcome.SUCCEEDED;
                case "FAILED" -> Outcome.FAILED;
                default -> Outcome.PENDING;
            };
        }

        private static ProviderResult result(Map<?, ?> body) {
            String status = text(body, "status");
            return new ProviderResult(text(body, "paymentId"), outcome(status), status, text(body, "failureCode"),
                    null, null, null);
        }
    }

    /** Masrvi : champs en français, montant en texte, états EN_COURS / SUCCES / ECHEC, codes 00/51/05/91. */
    record Masrvi(RestClient http) implements PaymentProvider {

        @Override
        public String code() {
            return "MASRVI";
        }

        @Override
        public ProviderResult initiate(PaymentRequest request) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reference", request.merchantReference());
            body.put("montant", request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            body.put("devise", request.currency());
            // Masrvi attend le numéro sans « + » : 22246000004
            body.put("telephoneClient", request.payerPhone() == null ? null : request.payerPhone().replace("+", ""));
            body.put("motif", request.description());
            return result(call("Masrvi", () -> http.post().uri("/transactions")
                    .header("Idempotency-Key", request.merchantReference())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class)));
        }

        @Override
        public ProviderResult fetchStatus(String providerReference) {
            return result(call("Masrvi", () -> http.get().uri("/transactions/{id}", providerReference)
                    .retrieve().body(Map.class)));
        }

        static Outcome outcome(String etat) {
            return switch (String.valueOf(etat)) {
                case "SUCCES" -> Outcome.SUCCEEDED;
                case "ECHEC" -> Outcome.FAILED;
                default -> Outcome.PENDING;
            };
        }

        static String failure(Outcome outcome, String code) {
            if (outcome != Outcome.FAILED) {
                return null;
            }
            return switch (String.valueOf(code)) {
                case "51" -> "INSUFFICIENT_FUNDS";
                case "91" -> "TIMEOUT";
                default -> "DECLINED";
            };
        }

        private static ProviderResult result(Map<?, ?> body) {
            String etat = text(body, "etat");
            Outcome outcome = outcome(etat);
            return new ProviderResult(text(body, "idTransaction"), outcome, etat + "/" + text(body, "code"),
                    failure(outcome, text(body, "code")), null, null, null);
        }
    }

    /** Passerelle carte : session de paiement + page hébergée ; statuts OPEN / AUTHORIZED / DECLINED / EXPIRED. */
    record Visa(RestClient http) implements PaymentProvider {

        @Override
        public String code() {
            return "VISA_GATEWAY";
        }

        @Override
        public ProviderResult initiate(PaymentRequest request) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderReference", request.merchantReference());
            body.put("amount", request.amount());
            body.put("currency", request.currency());
            body.put("returnUrl", request.returnUrl());
            body.put("cancelUrl", request.cancelUrl());
            return result(call("La passerelle bancaire", () -> http.post().uri("/checkout-sessions")
                    .header("Idempotency-Key", request.merchantReference())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class)));
        }

        @Override
        public ProviderResult fetchStatus(String providerReference) {
            return result(call("La passerelle bancaire", () -> http.get()
                    .uri("/checkout-sessions/{id}", providerReference).retrieve().body(Map.class)));
        }

        static Outcome outcome(String status) {
            return switch (String.valueOf(status)) {
                case "AUTHORIZED" -> Outcome.SUCCEEDED;
                case "DECLINED" -> Outcome.FAILED;
                case "EXPIRED" -> Outcome.CANCELLED;
                default -> Outcome.PENDING;
            };
        }

        static String failure(String declineReason) {
            if (declineReason == null) {
                return null;
            }
            return "insufficient_funds".equals(declineReason) ? "INSUFFICIENT_FUNDS" : "DECLINED";
        }

        private static ProviderResult result(Map<?, ?> body) {
            String status = text(body, "status");
            String maskedPan = body.get("card") instanceof Map<?, ?> card ? text(card, "maskedPan") : null;
            return new ProviderResult(text(body, "sessionId"), outcome(status), status,
                    failure(text(body, "declineReason")), null, text(body, "redirectUrl"), maskedPan);
        }
    }
}
