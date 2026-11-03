package mr.epayment.portal.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import mr.epayment.portal.catalog.PaymentCatalog;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.entitlement.EntitledMethod;
import mr.epayment.portal.entitlement.EntitlementEngine;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import mr.epayment.portal.transaction.TransactionStatistics;
import mr.epayment.portal.transaction.TransactionStatistics.Totals;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tableau de bord de l'abonné connecté : ses paiements du mois et l'utilisation de ses plafonds mensuels. */
@RestController
@RequestMapping("/api/abonne/tableau-de-bord")
class SubscriberDashboardController {

    private final CurrentSubscriber currentSubscriber;
    private final EntitlementEngine entitlements;
    private final TransactionStatistics statistics;
    private final PaymentCatalog catalog;

    SubscriberDashboardController(CurrentSubscriber currentSubscriber, EntitlementEngine entitlements,
            TransactionStatistics statistics, PaymentCatalog catalog) {
        this.currentSubscriber = currentSubscriber;
        this.entitlements = entitlements;
        this.statistics = statistics;
        this.catalog = catalog;
    }

    @GetMapping
    SubscriberDashboard dashboard(Authentication authentication) {
        UUID subscriberId = currentSubscriber.require(authentication).id();
        Instant now = Instant.now();
        Instant monthStart = LocalDate.now(AdminDashboardController.ZONE).withDayOfMonth(1)
                .atStartOfDay(AdminDashboardController.ZONE).toInstant();

        Totals month = statistics.totals(subscriberId, monthStart, now);
        Map<String, UUID> idsByCode = catalog.findAll().stream()
                .collect(Collectors.toMap(PaymentMethodView::code, PaymentMethodView::id));
        Map<UUID, BigDecimal> engaged = statistics.engagedPerMethod(subscriberId, monthStart);
        List<LimitUsage> usage = entitlements.methodsOf(subscriberId).stream()
                .map(m -> LimitUsage.of(m, engaged.getOrDefault(idsByCode.get(m.code()), BigDecimal.ZERO)))
                .toList();
        return new SubscriberDashboard(month.count(), month.succeeded(), month.failed(), month.collected(), usage);
    }

    /** @param paid montant des paiements réussis du mois */
    record SubscriberDashboard(long payments, long succeeded, long failed, BigDecimal paid, List<LimitUsage> limits) {
    }

    /** @param engaged paiements réussis et en cours du mois sur ce moyen (base du contrôle du plafond) */
    record LimitUsage(String code, String name, String shortLabel, String color, String logoUrl, String currency,
            BigDecimal monthlyLimit, BigDecimal engaged, BigDecimal remaining) {
        static LimitUsage of(EntitledMethod m, BigDecimal engaged) {
            return new LimitUsage(m.code(), m.name(), m.shortLabel(), m.color(), m.logoUrl(), m.currency(),
                    m.monthlyLimit(), engaged, m.monthlyLimit().subtract(engaged).max(BigDecimal.ZERO));
        }
    }
}
