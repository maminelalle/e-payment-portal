package mr.epayment.portal.gateway;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import mr.epayment.portal.gateway.PaymentProvider.PaymentRequest;
import mr.epayment.portal.gateway.PaymentProvider.ProviderResult;
import org.springframework.stereotype.Component;

/** Aiguillage vers l'adaptateur du fournisseur : un adaptateur par fournisseur, interface commune. */
@Component
public class PaymentGateway {

    private final Map<String, PaymentProvider> providers;

    PaymentGateway(List<PaymentProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toMap(PaymentProvider::code, Function.identity()));
    }

    public ProviderResult initiate(String providerCode, PaymentRequest request) {
        PaymentProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new IllegalStateException("Aucun adaptateur pour le fournisseur " + providerCode);
        }
        return provider.initiate(request);
    }
}
