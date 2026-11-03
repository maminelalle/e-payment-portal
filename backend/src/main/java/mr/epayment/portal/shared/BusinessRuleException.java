package mr.epayment.portal.shared;

/** Opération refusée par une règle métier (transition de statut interdite…) → HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
