package mr.epayment.portal.contract;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'un contrat (docs/conception, section 6). Seul ACTIVE donne accès aux moyens de paiement.
 * Semaine 5 : DRAFT → PENDING_SIGNATURE ; la suite (signature, activation, fin) arrive en semaines 6 et 7.
 */
public enum ContractStatus {
    DRAFT,
    PENDING_SIGNATURE,
    SIGNED,
    ACTIVE,
    SUSPENDED,
    EXPIRED,
    TERMINATED,
    /** Remplacé par un avenant signé et activé ; ses droits sont révoqués, son historique conservé. */
    REPLACED;

    /** Statuts qui empêchent d'envoyer un autre contrat au même abonné (hypothèse H3). */
    static final Set<ContractStatus> IN_PROGRESS = EnumSet.of(PENDING_SIGNATURE, SIGNED, ACTIVE,
            SUSPENDED);
}
