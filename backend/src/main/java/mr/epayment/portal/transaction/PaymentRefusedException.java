package mr.epayment.portal.transaction;

import mr.epayment.portal.entitlement.PaymentAuthorization.Refusal;

/** Paiement refusé par le moteur des droits (403 : pas le droit ; 422 : plafond dépassé). */
class PaymentRefusedException extends RuntimeException {

    private final Refusal refusal;

    PaymentRefusedException(Refusal refusal, String message) {
        super(message);
        this.refusal = refusal;
    }

    Refusal refusal() {
        return refusal;
    }

    boolean isLimit() {
        return refusal == Refusal.OVER_TRANSACTION_LIMIT || refusal == Refusal.OVER_MONTHLY_LIMIT;
    }
}
