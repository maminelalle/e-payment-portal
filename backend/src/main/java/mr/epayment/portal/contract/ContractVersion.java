package mr.epayment.portal.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une version d'un contrat : le contrat initial (amendmentNumber 0) ou l'un de ses avenants.
 *
 * @param endedAt fin d'effet : résiliation, ou remplacement par l'avenant suivant
 */
public record ContractVersion(UUID id, String reference, int amendmentNumber, ContractStatus status,
        LocalDate startDate, LocalDate endDate, Instant signedAt, Instant activatedAt, Instant endedAt,
        int methodCount) {
}
