package mr.epayment.portal.contract;

import java.util.UUID;

/** Publié quand l'abonné a signé : le moteur des droits (semaine 7) activera le contrat. */
public record ContractSigned(UUID contractId, String reference, UUID subscriberId) {
}
