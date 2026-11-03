package mr.epayment.portal.gateway;

import java.math.BigDecimal;

/**
 * Interface commune des fournisseurs de paiement (docs/03-api-fournisseurs-simules.md) :
 * un adaptateur par fournisseur, le reste du portail ne connaît que ce contrat.
 */
public interface PaymentProvider {

    /** Code du fournisseur au catalogue : BANKILY, MASRVI, VISA_GATEWAY. */
    String code();

    /**
     * Démarre le paiement. La référence du portail sert aussi de clé d'idempotence chez le fournisseur :
     * renvoyer la même demande ne crée pas de second paiement.
     *
     * @throws ProviderUnavailableException si le fournisseur ne répond pas ou refuse la demande
     */
    ProviderResult initiate(PaymentRequest request);

    /** Statut actuel chez le fournisseur (rapprochement, retour de la page carte). */
    ProviderResult fetchStatus(String providerReference);

    /**
     * @param merchantReference référence du portail (TRX-…), transmise au fournisseur
     * @param payerPhone        numéro du wallet payeur (wallets uniquement)
     * @param returnUrl         carte : retour du navigateur après la page hébergée
     */
    record PaymentRequest(String merchantReference, BigDecimal amount, String currency, String payerPhone,
            String description, String returnUrl, String cancelUrl) {
    }

    /**
     * @param providerStatus statut brut du fournisseur, conservé tel quel à côté du statut normalisé
     * @param redirectUrl    carte : page de paiement hébergée où envoyer l'abonné
     * @param maskedPan      carte : PAN masqué (jamais le numéro complet)
     */
    record ProviderResult(String providerReference, Outcome outcome, String providerStatus, String failureCode,
            String message, String redirectUrl, String maskedPan) {
    }

    /** Statut normalisé : le statut brut du fournisseur est toujours conservé à côté. */
    enum Outcome { SUCCEEDED, FAILED, PENDING, CANCELLED }

    /** Fournisseur injoignable, délai dépassé ou demande refusée. */
    class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
