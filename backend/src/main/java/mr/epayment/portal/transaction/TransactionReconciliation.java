package mr.epayment.portal.transaction;

import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâche de rapprochement, toutes les 2 minutes (une seule instance grâce à ShedLock) : les paiements en cours
 * depuis plus de 2 minutes sont vérifiés auprès de leur fournisseur.
 */
@Component
class TransactionReconciliation {

    private static final Logger log = LoggerFactory.getLogger(TransactionReconciliation.class);
    private static final Duration GRACE = Duration.ofMinutes(2);

    private final PaymentService payments;

    TransactionReconciliation(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${epayment.transactions.reconciliation-delay:PT2M}", initialDelayString = "PT1M")
    @SchedulerLock(name = "transactions-reconciliation", lockAtMostFor = "PT5M")
    void run() {
        int updated = payments.reconcileUnfinished(Instant.now().minus(GRACE));
        if (updated > 0) {
            log.info("Rapprochement : {} transaction(s) mise(s) à jour", updated);
        }
    }
}
