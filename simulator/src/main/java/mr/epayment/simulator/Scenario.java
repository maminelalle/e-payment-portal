package mr.epayment.simulator;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Scénarios de démonstration (docs/03-api-fournisseurs-simules.md), choisis par les centimes du montant
 * ou forcés par l'en-tête {@code X-Simulator-Scenario}.
 */
public enum Scenario {
    /** Succès après le délai normal de confirmation. */
    SUCCESS,
    /** Échec générique (montant en ,13). */
    FAILED,
    /** Solde insuffisant (montant en ,51). */
    INSUFFICIENT_FUNDS,
    /** Reste en attente longtemps puis réussit (montant en ,99) : teste le rapprochement. */
    DELAYED,
    /** Réussit sans envoyer de webhook (montant en ,98) : seul le rapprochement le découvre. */
    NO_WEBHOOK;

    public static Scenario of(BigDecimal amount, String forced) {
        if (forced != null && !forced.isBlank()) {
            return Scenario.valueOf(forced.trim().toUpperCase(Locale.ROOT));
        }
        int cents = amount.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        return switch (cents) {
            case 13 -> FAILED;
            case 51 -> INSUFFICIENT_FUNDS;
            case 99 -> DELAYED;
            case 98 -> NO_WEBHOOK;
            default -> SUCCESS;
        };
    }

    /** Scénario d'une carte de test (passerelle Visa). */
    public static Scenario ofTestCard(String cardNumber) {
        return switch (cardNumber) {
            case "4111111111111111" -> SUCCESS;
            case "4000000000009995" -> INSUFFICIENT_FUNDS;
            case "4000000000003220" -> DELAYED;
            default -> FAILED;
        };
    }
}
