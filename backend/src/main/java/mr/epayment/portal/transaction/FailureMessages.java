package mr.epayment.portal.transaction;

/** Message affiché à l'abonné pour chaque cause d'échec normalisée. */
final class FailureMessages {

    private FailureMessages() {
    }

    static String of(String failureCode) {
        return switch (String.valueOf(failureCode)) {
            case "INSUFFICIENT_FUNDS" -> "Solde insuffisant.";
            case "TIMEOUT" -> "Délai de confirmation dépassé.";
            case "PROVIDER_UNAVAILABLE" -> "Le fournisseur est momentanément indisponible : rien n'a été débité. Réessayez.";
            default -> "Paiement refusé par le fournisseur.";
        };
    }
}
