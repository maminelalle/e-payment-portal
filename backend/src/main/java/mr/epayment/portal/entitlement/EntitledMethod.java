package mr.epayment.portal.entitlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Moyen de paiement auquel l'abonné a droit (« Mes moyens de paiement »), ou droit d'un contrat (vue admin).
 *
 * @param available        utilisable aujourd'hui (droit actif, dans sa période, moyen actif au catalogue)
 * @param unavailableReason pourquoi il ne l'est pas, sinon {@code null}
 */
public record EntitledMethod(
        UUID entitlementId,
        UUID contractId,
        String code,
        String name,
        String category,
        String shortLabel,
        String color,
        String logoUrl,
        String description,
        String instructions,
        String conditions,
        String currency,
        BigDecimal perTransactionLimit,
        BigDecimal monthlyLimit,
        LocalDate validFrom,
        LocalDate validTo,
        String status,
        boolean available,
        String unavailableReason) {
}
