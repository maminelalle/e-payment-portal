package mr.epayment.portal.signature;

/** Code refusé (faux ou expiré) → HTTP 422 avec le nombre d'essais restants. */
class OtpRejectedException extends RuntimeException {

    private final int remainingAttempts;

    OtpRejectedException(String message, int remainingAttempts) {
        super(message);
        this.remainingAttempts = remainingAttempts;
    }

    int remainingAttempts() {
        return remainingAttempts;
    }
}
