package mr.epayment.portal.transaction;

/** Statut interne d'une transaction (le statut brut du fournisseur est conservé à côté). */
public enum TransactionStatus {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REFUNDED;

    boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == REFUNDED;
    }
}
