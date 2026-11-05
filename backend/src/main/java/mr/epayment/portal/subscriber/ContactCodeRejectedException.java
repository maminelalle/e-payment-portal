package mr.epayment.portal.subscriber;

/** Code de changement de coordonnées refusé (faux, expiré, demande close) → HTTP 422 avec les essais restants. */
class ContactCodeRejectedException extends RuntimeException {

    private final int remainingAttempts;

    ContactCodeRejectedException(String message, int remainingAttempts) {
        super(message);
        this.remainingAttempts = remainingAttempts;
    }

    int remainingAttempts() {
        return remainingAttempts;
    }
}
