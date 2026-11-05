package mr.epayment.portal.dashboard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import mr.epayment.portal.catalog.PaymentCatalog;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.contract.ContractService;
import mr.epayment.portal.contract.ContractStatus;
import mr.epayment.portal.contract.ContractView;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberStatus;
import mr.epayment.portal.transaction.PaymentService;
import mr.epayment.portal.transaction.PaymentService.Filter;
import mr.epayment.portal.transaction.TransactionStatistics;
import mr.epayment.portal.transaction.TransactionStatistics.MethodCount;
import mr.epayment.portal.transaction.TransactionStatistics.StatusCount;
import mr.epayment.portal.transaction.TransactionStatistics.Totals;
import mr.epayment.portal.transaction.TransactionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tableau de bord administrateur (maquette A1 et conception §3.1). Les indicateurs du mois sont comparés à la
 * même durée écoulée du mois précédent (du 1er au même jour), pour une comparaison juste en cours de mois.
 * Abonnés et contrats : état actuel, par statut. Transactions : sur la période choisie, par statut, par moyen,
 * par type (wallet ou carte) et par fournisseur.
 */
@RestController
@RequestMapping("/api/admin/tableau-de-bord")
class AdminDashboardController {

    static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");

    private final SubscriberService subscribers;
    private final ContractService contracts;
    private final PaymentService payments;
    private final TransactionStatistics statistics;
    private final PaymentCatalog catalog;

    AdminDashboardController(SubscriberService subscribers, ContractService contracts, PaymentService payments,
            TransactionStatistics statistics, PaymentCatalog catalog) {
        this.subscribers = subscribers;
        this.contracts = contracts;
        this.payments = payments;
        this.statistics = statistics;
        this.catalog = catalog;
    }

    /** @param jours période du graphique et de la répartition (7, 30 ou 90 jours) */
    @GetMapping
    AdminDashboard dashboard(@RequestParam(defaultValue = "30") int jours) {
        int days = jours <= 7 ? 7 : jours <= 30 ? 30 : 90;
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        Instant previousStart = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(ZONE).toInstant();
        Instant previousEnd = min(previousStart.plus(Duration.between(monthStart, now)), monthStart);

        Totals month = statistics.totals(null, monthStart, now);
        Totals previous = statistics.totals(null, previousStart, previousEnd);

        LocalDate firstDay = today.minusDays(days - 1L);
        Instant periodStart = firstDay.atStartOfDay(ZONE).toInstant();
        Map<UUID, PaymentMethodView> methods = catalog.findAll().stream()
                .collect(Collectors.toMap(PaymentMethodView::id, Function.identity()));
        List<MethodCount> methodCounts = statistics.perMethod(null, periodStart, now);
        List<MethodShare> perMethod = methodCounts.stream()
                .map(m -> MethodShare.of(methods.get(m.methodId()), m.count(), m.collected()))
                .toList();
        Map<SubscriberStatus, Long> subscriberCounts = subscribers.countByStatus();

        return new AdminDashboard(
                subscriberCounts.get(SubscriberStatus.ACTIVE),
                subscribers.createdSince(monthStart),
                contracts.countByStatus(ContractStatus.PENDING_SIGNATURE),
                new MonthFigure(month.count(), previous.count()),
                new MonthFigure(month.collected(), previous.collected()),
                days,
                statistics.perDay(null, firstDay, today).stream()
                        .map(d -> new DayPoint(d.day(), d.count(), d.collected())).toList(),
                perMethod,
                contracts.toProcess().stream().map(ContractToProcess::of).toList(),
                payments.search(new Filter(null, null, null, null, null, null), 0, 5).content(),
                subscriberCounts,
                contracts.countPerStatus(),
                statistics.perStatus(periodStart, now),
                group(methodCounts, methods, m -> m.category(), m -> "CARD".equals(m.category()) ? "Cartes bancaires" : "Wallets"),
                group(methodCounts, methods, m -> m.provider().code(), m -> m.provider().name()));
    }

    /** Regroupe les chiffres par moyen selon une clé du moyen (type, fournisseur), du plus utilisé au moins utilisé. */
    private static List<Share> group(List<MethodCount> counts, Map<UUID, PaymentMethodView> methods,
            Function<PaymentMethodView, String> key, Function<PaymentMethodView, String> label) {
        Map<String, Share> shares = new LinkedHashMap<>();
        for (MethodCount count : counts) {
            PaymentMethodView method = methods.get(count.methodId());
            shares.merge(key.apply(method), new Share(key.apply(method), label.apply(method), count.count(),
                    count.collected()), Share::plus);
        }
        return shares.values().stream()
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    record AdminDashboard(long activeSubscribers, long newSubscribersThisMonth, long contractsPendingSignature,
            MonthFigure transactionsThisMonth, MonthFigure collectedThisMonth, int days, List<DayPoint> perDay,
            List<MethodShare> perMethod, List<ContractToProcess> contractsToProcess,
            List<TransactionView> latestTransactions, Map<SubscriberStatus, Long> subscribersByStatus,
            Map<ContractStatus, Long> contractsByStatus, List<StatusCount> transactionsByStatus,
            List<Share> perCategory, List<Share> perProvider) {
    }

    /** Part d'un regroupement de moyens (type ou fournisseur) sur la période. */
    record Share(String code, String name, long count, BigDecimal collected) {
        Share plus(Share other) {
            return new Share(code, name, count + other.count, collected.add(other.collected));
        }
    }

    /** @param previous même durée écoulée du mois précédent */
    record MonthFigure(Number current, Number previous) {
    }

    record DayPoint(LocalDate day, long count, BigDecimal collected) {
    }

    record MethodShare(String code, String name, String color, long count, BigDecimal collected) {
        static MethodShare of(PaymentMethodView method, long count, BigDecimal collected) {
            return new MethodShare(method.code(), method.name(), method.color(), count, collected);
        }
    }

    record ContractToProcess(UUID id, String reference, ContractStatus status, String subscriberName,
            Instant createdAt, Instant sentAt, Instant signedAt) {
        static ContractToProcess of(ContractView c) {
            return new ContractToProcess(c.id(), c.reference(), c.status(), c.subscriber().displayName(),
                    c.createdAt(), c.sentAt(), c.signedAt());
        }
    }
}
