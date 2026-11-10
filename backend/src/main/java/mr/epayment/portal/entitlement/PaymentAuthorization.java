package mr.epayment.portal.entitlement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Résultat du contrôle serveur effectué avant chaque paiement.
 *
 * @param contractId     contrat qui autorise le paiement (à rattacher à la transaction)
 * @param commissionRate taux de commission de ce contrat, en % (null : aucune commission)
 */
public record PaymentAuthorization(boolean allowed, Refusal refusal, String message, UUID contractId,
        UUID paymentMethodId, String methodName, String category, String providerCode, String currency,
        BigDecimal perTransactionLimit, BigDecimal monthlyLimit, BigDecimal commissionRate) {

    public enum Refusal {
        /** Le moyen n'est pas inscrit au contrat actif de l'abonné. */
        NOT_ENTITLED,
        /** Compte de l'abonné suspendu ou désactivé. */
        SUBSCRIBER_BLOCKED,
        /** Contrat pas encore en vigueur ou déjà échu. */
        OUTSIDE_VALIDITY,
        /** Moyen désactivé au catalogue par l'administrateur. */
        METHOD_DISABLED,
        OVER_TRANSACTION_LIMIT,
        OVER_MONTHLY_LIMIT
    }

    static PaymentAuthorization refused(Refusal refusal, String message) {
        return new PaymentAuthorization(false, refusal, message, null, null, null, null, null, null, null, null, null);
    }
}
