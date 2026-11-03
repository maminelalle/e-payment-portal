package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transaction telle qu'affichée (résultat de paiement, historique).
 *
 * @param redirectUrl carte en cours : page hébergée de la passerelle où l'abonné saisit sa carte
 */
public record TransactionView(
        String reference,
        TransactionStatus status,
        String providerStatus,
        String methodCode,
        String methodName,
        String category,
        String shortLabel,
        String color,
        String logoUrl,
        BigDecimal amount,
        String currency,
        String description,
        String payerPhone,
        String cardMaskedPan,
        String failureCode,
        String failureMessage,
        String redirectUrl,
        UUID contractId,
        Instant createdAt,
        Instant completedAt) {
}
