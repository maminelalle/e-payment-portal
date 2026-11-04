package mr.epayment.portal.transaction;

import java.math.BigDecimal;

/** Totaux d'une recherche de transactions : nombre (tous statuts) et montant encaissé (réussies seulement). */
public record FilteredTotals(long count, BigDecimal collected) {
}
