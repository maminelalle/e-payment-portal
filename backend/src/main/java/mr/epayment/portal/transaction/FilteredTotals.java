package mr.epayment.portal.transaction;

import java.math.BigDecimal;

/**
 * Totaux d'une recherche de transactions : nombre (tous statuts), montant encaissé et commissions dues
 * (réussies seulement).
 */
public record FilteredTotals(long count, BigDecimal collected, BigDecimal commissions) {
}
