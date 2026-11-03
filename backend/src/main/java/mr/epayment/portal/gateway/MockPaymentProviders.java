package mr.epayment.portal.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fournisseurs simulés en mémoire, sans réseau ({@code epayment.providers.mode=mock}) : utilisés par les tests
 * d'intégration du portail. Réponse immédiate, mêmes scénarios que le simulateur (centimes du montant) :
 * {@code ,13} échec · {@code ,51} solde insuffisant · {@code ,99} en attente · sinon succès.
 */
@Configuration
@ConditionalOnProperty(name = "epayment.providers.mode", havingValue = "mock")
class MockPaymentProviders {

    @Bean
    PaymentProvider bankilyProvider() {
        return new Mock("BANKILY", "BKL", "SUCCESS", "FAILED", "PENDING");
    }

    @Bean
    PaymentProvider masrviProvider() {
        return new Mock("MASRVI", "MSV", "SUCCES", "ECHEC", "EN_COURS");
    }

    @Bean
    PaymentProvider visaGatewayProvider() {
        return new Mock("VISA_GATEWAY", "cs", "AUTHORIZED", "DECLINED", "OPEN");
    }

    record Mock(String code, String prefix, String success, String failure, String pending) implements PaymentProvider {

        @Override
        public ProviderResult initiate(PaymentRequest request) {
            String reference = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
            int cents = request.amount().remainder(BigDecimal.ONE).movePointRight(2).intValue();
            return switch (cents) {
                case 13 -> result(reference, Outcome.FAILED, failure, "DECLINED");
                case 51 -> result(reference, Outcome.FAILED, failure, "INSUFFICIENT_FUNDS");
                case 99 -> result(reference, Outcome.PENDING, pending, null);
                default -> result(reference, Outcome.SUCCEEDED, success, null);
            };
        }

        @Override
        public ProviderResult fetchStatus(String providerReference) {
            return result(providerReference, Outcome.SUCCEEDED, success, null);
        }

        private static ProviderResult result(String reference, Outcome outcome, String status, String failure) {
            return new ProviderResult(reference, outcome, status, failure, null, null, null);
        }
    }
}
