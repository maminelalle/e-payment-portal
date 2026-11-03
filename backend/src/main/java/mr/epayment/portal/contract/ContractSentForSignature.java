package mr.epayment.portal.contract;

import java.util.UUID;

/** Publié après l'envoi d'un contrat pour signature (le module notification prévient l'abonné). */
public record ContractSentForSignature(UUID contractId, String reference, String subscriberEmail,
        String subscriberName) {
}
