package mr.epayment.portal.shared;

import java.util.Map;

/** Règle de validation qui porte sur plusieurs champs (NNI exigé pour une personne physique…) → HTTP 400. */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super("Certains champs sont invalides.");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
