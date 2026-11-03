package mr.epayment.portal.gateway;

import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres {@code epayment.providers.*} : adresse, clé d'API et secret des webhooks de chaque fournisseur. */
@ConfigurationProperties("epayment.providers")
record ProvidersProperties(Duration webhookTolerance, Provider bankily, Provider masrvi, Provider visa) {

    record Provider(String baseUrl, String apiKey, String webhookSecret) {
    }

    /** Fournisseur désigné par le chemin du webhook ({@code /api/webhooks/bankily}…), avec son code catalogue. */
    Optional<Named> byWebhookPath(String path) {
        return Optional.ofNullable(switch (path) {
            case "bankily" -> new Named("BANKILY", bankily);
            case "masrvi" -> new Named("MASRVI", masrvi);
            case "visa" -> new Named("VISA_GATEWAY", visa);
            default -> null;
        });
    }

    record Named(String code, Provider settings) {
    }
}
