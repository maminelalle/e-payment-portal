package mr.epayment.portal.shared;

/** Conflit avec l'existant (doublon d'email, de NNI…) → HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
