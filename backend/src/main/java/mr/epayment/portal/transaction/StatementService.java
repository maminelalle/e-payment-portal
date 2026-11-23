package mr.epayment.portal.transaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mr.epayment.portal.contract.ContractService;
import mr.epayment.portal.document.PdfRenderer;
import mr.epayment.portal.shared.Amounts;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberType;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relevé mensuel d'un abonné en PDF (et facture des commissions du mois) : chaque paiement du mois, les totaux,
 * la répartition par moyen et les commissions dues au titre du contrat (article 4). Généré à la demande, à partir
 * des transactions : il reflète toujours l'état courant (un paiement en attente peut encore aboutir).
 */
@Service
class StatementService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final PaymentService payments;
    private final SubscriberService subscribers;
    private final ContractService contracts;
    private final PdfRenderer pdfRenderer;
    private final String template;

    StatementService(PaymentService payments, SubscriberService subscribers, ContractService contracts,
            PdfRenderer pdfRenderer) {
        this.payments = payments;
        this.subscribers = subscribers;
        this.contracts = contracts;
        this.pdfRenderer = pdfRenderer;
        try {
            this.template = new ClassPathResource("documents/releve.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Modèle de relevé introuvable", e);
        }
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID subscriberId, String month) {
        YearMonth period = parse(month);
        SubscriberView subscriber = subscribers.get(subscriberId);
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        List<TransactionView> rows = payments.periodRows(subscriberId, from, to);
        byte[] pdf = pdfRenderer.renderPdf(template, variables(subscriber, period, from, to, rows));
        return new Statement("releve-%s-%s.pdf".formatted(subscriber.reference(), period), pdf);
    }

    private Map<String, Object> variables(SubscriberView subscriber, YearMonth period, LocalDate from, LocalDate to,
            List<TransactionView> rows) {
        String currency = rows.isEmpty() ? "MRU" : rows.getFirst().currency();
        long succeeded = rows.stream().filter(t -> t.status() == TransactionStatus.SUCCEEDED).count();
        long failed = rows.stream().filter(t -> t.status() == TransactionStatus.FAILED).count();
        BigDecimal collected = sum(rows, t -> t.status() == TransactionStatus.SUCCEEDED ? t.amount() : null);
        BigDecimal commissions = sum(rows, TransactionView::commissionAmount);

        // Par moyen, dans l'ordre d'apparition : nombre de paiements, encaissé, commissions
        Map<String, MethodTotals> byMethod = new LinkedHashMap<>();
        for (TransactionView t : rows) {
            byMethod.computeIfAbsent(t.methodName(), k -> new MethodTotals()).add(t);
        }
        List<Map<String, Object>> methods = new ArrayList<>();
        byMethod.forEach((name, totals) -> methods.add(Map.of("name", name, "count", totals.count,
                "collected", money(totals.collected, currency), "commission", money(totals.commission, currency))));

        List<Map<String, Object>> lines = rows.stream().map(t -> {
            Map<String, Object> line = new HashMap<>();
            line.put("date", DATE_TIME.format(t.createdAt()));
            line.put("reference", t.reference());
            line.put("method", t.methodName());
            line.put("amount", Amounts.format(t.amount()));
            line.put("commission", t.commissionAmount() == null ? "—" : Amounts.format(t.commissionAmount()));
            line.put("status", TransactionCsv.label(t.status()));
            line.put("succeeded", t.status() == TransactionStatus.SUCCEEDED);
            line.put("description", t.description());
            return line;
        }).toList();

        List<String> contractRefs = rows.stream().map(TransactionView::contractId).filter(Objects::nonNull).distinct()
                .map(id -> contracts.get(id).reference()).toList();
        boolean legalEntity = subscriber.type() == SubscriberType.MORALE;

        Map<String, Object> vars = new HashMap<>();
        vars.put("month", capitalize(MONTH.format(period)));
        vars.put("from", DAY.format(from));
        vars.put("to", DAY.format(to));
        vars.put("issuedAt", DAY.format(LocalDate.now(ZONE)));
        vars.put("subscriber", Map.of(
                "name", subscriber.displayName(),
                "reference", subscriber.reference(),
                "identifierLabel", legalEntity ? "NIF" : "NNI",
                "identifier", Objects.requireNonNullElse(legalEntity ? subscriber.nif() : subscriber.nni(), "—"),
                "email", subscriber.email(),
                "address", Objects.requireNonNullElse(subscriber.address(), "—")));
        vars.put("contracts", contractRefs.isEmpty() ? "—" : String.join(", ", contractRefs));
        vars.put("count", rows.size());
        vars.put("succeeded", succeeded);
        vars.put("failed", failed);
        vars.put("collected", money(collected, currency));
        vars.put("commissions", money(commissions, currency));
        vars.put("methods", methods);
        vars.put("lines", lines);
        vars.put("currency", currency);
        return vars;
    }

    /** « 2026-11 » ; refuse un mois mal écrit ou à venir. */
    private static YearMonth parse(String month) {
        YearMonth period;
        try {
            period = YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new FieldValidationException(Map.of("mois", "Mois attendu au format AAAA-MM (ex. 2026-11)."));
        }
        if (period.isAfter(YearMonth.now(ZONE))) {
            throw new FieldValidationException(Map.of("mois", "Ce mois n'est pas encore commencé."));
        }
        return period;
    }

    private static BigDecimal sum(List<TransactionView> rows, java.util.function.Function<TransactionView, BigDecimal> value) {
        return rows.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String money(BigDecimal value, String currency) {
        return Amounts.format(value) + " " + currency;
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase(Locale.FRENCH) + text.substring(1);
    }

    private static final class MethodTotals {
        long count;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;

        void add(TransactionView t) {
            count++;
            if (t.status() == TransactionStatus.SUCCEEDED) {
                collected = collected.add(t.amount());
            }
            if (t.commissionAmount() != null) {
                commission = commission.add(t.commissionAmount());
            }
        }
    }

    record Statement(String fileName, byte[] content) {
    }
}
