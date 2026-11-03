package mr.epayment.portal.gateway;

import java.math.BigDecimal;

/**
 * Interface commune des fournisseurs de paiement (docs/03-api-fournisseurs-simules.md).
 * Semaine 7 : adaptateur simulé en mémoire ; semaines 8-9 : adaptateurs HTTP vers le simulateur
 * (consultation du statut et webhooks signés s'ajoutent alors à cette interface).
 */
public interface PaymentProvider {

    /** Code du fournisseur au catalogue : BANKILY, MASRVI, VISA_GATEWAY. */
    String code();

    ProviderResult initiate(PaymentRequest request);

    /**
     * @param merchantReference référence du portail, transmise au fournisseur
     * @param payerPhone        numéro du wallet payeur (wallets uniquement)
     */
    record PaymentRequest(String merchantReference, BigDecimal amount, String currency, String payerPhone,
            String description) {
    }

    /**
     * @param providerStatus statut brut du fournisseur, conservé tel quel à côté du statut normalisé
     */
    record ProviderResult(String providerReference, Outcome outcome, String providerStatus, String failureCode,
            String message) {
    }

    enum Outcome { SUCCEEDED, FAILED, PENDING }
}
