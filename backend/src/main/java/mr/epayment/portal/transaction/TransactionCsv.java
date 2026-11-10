package mr.epayment.portal.transaction;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import mr.epayment.portal.shared.Amounts;

/**
 * Historique des transactions en CSV pour Excel en français : séparateur « ; », BOM UTF-8 (accents et arabe),
 * montants « 12 500,00 », heure de Nouakchott.
 */
final class TransactionCsv {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Africa/Nouakchott"));
    private static final Map<TransactionStatus, String> LABELS = Map.of(
            TransactionStatus.CREATED, "Créée",
            TransactionStatus.PENDING, "En attente",
            TransactionStatus.SUCCEEDED, "Réussie",
            TransactionStatus.FAILED, "Échouée",
            TransactionStatus.CANCELLED, "Annulée",
            TransactionStatus.REFUNDED, "Remboursée");

    private TransactionCsv() {
    }

    static byte[] write(List<TransactionView> rows, boolean withSubscriber) {
        StringBuilder csv = new StringBuilder("﻿");
        line(csv, withSubscriber
                ? List.of("Référence", "Date", "Abonné", "Référence abonné", "Moyen", "Montant", "Commission",
                        "Devise", "Statut", "Motif", "Référence fournisseur", "Raison de l'échec")
                : List.of("Référence", "Date", "Moyen", "Montant", "Commission", "Devise", "Statut", "Motif",
                        "Référence fournisseur", "Raison de l'échec"));
        for (TransactionView t : rows) {
            String date = DATE_TIME.format(t.createdAt());
            String amount = Amounts.format(t.amount());
            String commission = t.commissionAmount() == null ? "" : Amounts.format(t.commissionAmount());
            String status = LABELS.get(t.status());
            line(csv, withSubscriber
                    ? List.of(t.reference(), date, nn(t.subscriberName()), nn(t.subscriberReference()), t.methodName(),
                            amount, commission, t.currency(), status, nn(t.description()), nn(t.providerReference()),
                            nn(t.failureMessage()))
                    : List.of(t.reference(), date, t.methodName(), amount, commission, t.currency(), status,
                            nn(t.description()),
                            nn(t.providerReference()), nn(t.failureMessage())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(';');
            }
            csv.append(escape(cells.get(i)));
        }
        csv.append("\r\n");
    }

    /** Guillemets si besoin ; une cellule commençant par = + - @ est neutralisée (injection de formule Excel). */
    static String escape(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }
}
