package mr.epayment.portal.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fournisseurs simulés en mémoire (semaine 7), en attendant les adaptateurs HTTP vers le simulateur.
 * Mêmes scénarios que le simulateur, choisis par les centimes du montant :
 * {@code ,13} échec · {@code ,51} solde insuffisant · {@code ,99} en attente · sinon succès.
 */
@Configuration
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
                case 13 -> new ProviderResult(reference, Outcome.FAILED, failure, "DECLINED",
                        "Paiement refusé par le fournisseur.");
                case 51 -> new ProviderResult(reference, Outcome.FAILED, failure, "INSUFFICIENT_FUNDS",
                        "Solde insuffisant.");
                case 99 -> new ProviderResult(reference, Outcome.PENDING, pending, null,
                        "Paiement en attente de confirmation du fournisseur.");
                default -> new ProviderResult(reference, Outcome.SUCCEEDED, success, null, "Paiement accepté.");
            };
        }
    }
}
