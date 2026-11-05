package mr.epayment.portal.contract;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.subscriber.SubscriberType;
import mr.epayment.portal.subscriber.SubscriberView;

/**
 * Variables du modèle de contrat. Les valeurs sont déjà mises en forme (dates, montants) pour que
 * le modèle HTML reste simple. Les montants utilisent des espaces simples : la police intégrée du PDF
 * ne contient pas l'espace fine insécable de la locale française.
 */
final class ContractDocument {

    static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ContractDocument() {
    }

    /** @param replaces référence du contrat remplacé, pour un avenant ; sinon null */
    static Map<String, Object> variables(Contract contract, SubscriberView subscriber,
            Map<UUID, PaymentMethodView> catalog, boolean draft, String replaces) {
        Map<String, Object> contractVars = new HashMap<>();
        contractVars.put("reference", contract.getReference());
        contractVars.put("startDate", format(contract.getStartDate()));
        contractVars.put("endDate", format(contract.getEndDate()));
        contractVars.put("commission", contract.getCommissionRate() == null ? null
                : decimal(contract.getCommissionRate()) + " %");
        contractVars.put("draft", draft);
        contractVars.put("amendment", replaces == null ? null
                : Map.of("number", contract.getAmendmentNumber(), "replaces", replaces));

        boolean legalEntity = subscriber.type() == SubscriberType.MORALE;
        Map<String, Object> subscriberVars = new HashMap<>();
        subscriberVars.put("displayName", subscriber.displayName());
        subscriberVars.put("typeLabel", legalEntity ? "personne morale" : "personne physique");
        subscriberVars.put("identifierLabel", legalEntity ? "NIF" : "NNI");
        subscriberVars.put("identifier", legalEntity ? subscriber.nif() : subscriber.nni());
        subscriberVars.put("address", subscriber.address() == null ? "—" : subscriber.address());
        subscriberVars.put("phone", subscriber.phone());
        subscriberVars.put("email", subscriber.email());

        List<Map<String, Object>> methods = contract.getMethods().stream().map(included -> {
            PaymentMethodView method = catalog.get(included.paymentMethodId());
            Map<String, Object> m = new HashMap<>();
            m.put("name", method.name());
            m.put("categoryLabel", "CARD".equals(method.category()) ? "Carte bancaire" : "Wallet mobile");
            m.put("perTransactionLimit", amount(included.perTransactionLimit(), method.currency()));
            m.put("monthlyLimit", amount(included.monthlyLimit(), method.currency()));
            return m;
        }).toList();

        return Map.of(
                "contract", contractVars,
                "subscriber", subscriberVars,
                "methods", methods,
                "issuedAt", format(LocalDate.now(ZONE)));
    }

    static String amount(BigDecimal value, String currency) {
        return decimal(value) + " " + currency;
    }

    private static String decimal(BigDecimal value) {
        var symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }

    private static String format(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }
}
