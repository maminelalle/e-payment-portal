package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import mr.epayment.portal.shared.Amounts;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transactions suspectes, recalculées à chaque consultation sur les 7 derniers jours :
 * <ul>
 *   <li>montant inhabituel : au moins 3 fois la moyenne des paiements réussis de l'abonné sur les 90 jours
 *       précédents (avec au moins 3 paiements de référence) ;</li>
 *   <li>échecs répétés : au moins 3 paiements échoués du même abonné en une heure.</li>
 * </ul>
 * Simple signal pour l'administrateur : rien n'est bloqué automatiquement.
 */
@Service
class TransactionAlerts {

    static final int UNUSUAL_FACTOR = 3;
    static final int MIN_HISTORY = 3;
    static final int FAILURE_BURST = 3;

    private final TransactionRepository transactions;
    private final SubscriberService subscribers;

    TransactionAlerts(TransactionRepository transactions, SubscriberService subscribers) {
        this.transactions = transactions;
        this.subscribers = subscribers;
    }

    @Transactional(readOnly = true)
    List<Alert> current(Instant now) {
        Instant since = now.minus(Duration.ofDays(7));
        List<PaymentTransaction> recent = transactions.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since);
        if (recent.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> averages = new HashMap<>();
        for (Object[] row : transactions.successAverages(since.minus(Duration.ofDays(90)), since)) {
            if (((Number) row[2]).longValue() >= MIN_HISTORY) {
                averages.put((UUID) row[0], BigDecimal.valueOf(((Number) row[1]).doubleValue()));
            }
        }
        Map<UUID, SubscriberView> parties = subscribers.findAllById(
                recent.stream().map(PaymentTransaction::getSubscriberId).collect(Collectors.toSet()));

        List<Alert> alerts = new ArrayList<>();
        for (PaymentTransaction tx : recent) {
            BigDecimal average = averages.get(tx.getSubscriberId());
            if (average != null && tx.getStatus() != TransactionStatus.CANCELLED
                    && tx.getAmount().compareTo(average.multiply(BigDecimal.valueOf(UNUSUAL_FACTOR))) >= 0) {
                alerts.add(new Alert("UNUSUAL_AMOUNT", tx.getReference(), name(parties, tx), tx.getCreatedAt(),
                        "Montant de %s %s, %s fois la moyenne habituelle (%s %s)".formatted(
                                Amounts.format(tx.getAmount()), tx.getCurrency(),
                                tx.getAmount().divide(average, 0, RoundingMode.DOWN),
                                Amounts.format(average.setScale(2, RoundingMode.HALF_UP)), tx.getCurrency())));
            }
        }

        // Échecs groupés par abonné, du plus ancien au plus récent : fenêtre glissante d'une heure.
        Map<UUID, List<PaymentTransaction>> failures = recent.stream()
                .filter(t -> t.getStatus() == TransactionStatus.FAILED)
                .sorted(Comparator.comparing(PaymentTransaction::getCreatedAt))
                .collect(Collectors.groupingBy(PaymentTransaction::getSubscriberId));
        failures.forEach((subscriberId, list) -> {
            for (int end = list.size() - 1; end >= FAILURE_BURST - 1; end--) {
                PaymentTransaction last = list.get(end);
                PaymentTransaction first = list.get(end - FAILURE_BURST + 1);
                if (Duration.between(first.getCreatedAt(), last.getCreatedAt()).compareTo(Duration.ofHours(1)) <= 0) {
                    alerts.add(new Alert("REPEATED_FAILURES", last.getReference(), name(parties, last),
                            last.getCreatedAt(), "%d paiements échoués en moins d'une heure".formatted(FAILURE_BURST)));
                    break;
                }
            }
        });
        alerts.sort(Comparator.comparing(Alert::at).reversed());
        return alerts;
    }

    private static String name(Map<UUID, SubscriberView> parties, PaymentTransaction tx) {
        SubscriberView s = parties.get(tx.getSubscriberId());
        return s == null ? "" : s.displayName();
    }

    /** @param type UNUSUAL_AMOUNT ou REPEATED_FAILURES */
    record Alert(String type, String reference, String subscriberName, Instant at, String detail) {
    }

    @RestController
    @RequestMapping("/api/admin/alertes")
    static class Controller {

        private final TransactionAlerts alerts;

        Controller(TransactionAlerts alerts) {
            this.alerts = alerts;
        }

        @GetMapping
        List<Alert> list() {
            return alerts.current(Instant.now());
        }
    }
}
