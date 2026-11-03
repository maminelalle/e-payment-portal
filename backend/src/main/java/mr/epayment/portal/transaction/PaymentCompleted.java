package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Publié une seule fois, quand un paiement passe à « réussi » ou « échoué » (initiation, webhook, consultation
 * ou rapprochement). Un abandon par l'abonné n'est pas publié : il l'a fait lui-même.
 */
public record PaymentCompleted(String reference, UUID subscriberId, String subscriberEmail, String subscriberName,
        boolean succeeded, String methodName, BigDecimal amount, String currency, String failureMessage,
        Instant completedAt) {
}
