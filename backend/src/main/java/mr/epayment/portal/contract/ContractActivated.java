package mr.epayment.portal.contract;

import java.util.UUID;

/** Publié quand un contrat devient actif (le module notification prévient l'abonné). */
public record ContractActivated(UUID contractId, String reference, UUID subscriberId, String subscriberEmail,
        String subscriberName) {
}
