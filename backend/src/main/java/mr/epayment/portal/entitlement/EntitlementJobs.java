package mr.epayment.portal.entitlement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import mr.epayment.portal.contract.ContractSigned;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Déclencheurs du moteur des droits :
 * <ul>
 *   <li>activation automatique dès que la signature est enregistrée (après validation de la transaction) ;</li>
 *   <li>tâche quotidienne (00 h 05, heure de Nouakchott) : expiration des contrats échus et rattrapage des
 *       contrats signés non activés. ShedLock garantit une seule exécution même avec plusieurs instances.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class EntitlementJobs {

    private static final Logger log = LoggerFactory.getLogger(EntitlementJobs.class);

    private final EntitlementEngine engine;

    EntitlementJobs(EntitlementEngine engine) {
        this.engine = engine;
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    /**
     * Activation automatique. Si elle échoue (document altéré…), le contrat reste « signé » :
     * la tâche quotidienne ou l'administrateur la relancent, sans risque de doublon.
     */
    @TransactionalEventListener
    void onContractSigned(ContractSigned event) {
        try {
            engine.activateContract(event.contractId());
        } catch (RuntimeException e) {
            log.error("Activation automatique du contrat {} impossible : il reste « signé »", event.reference(), e);
        }
    }

    @Scheduled(cron = "${epayment.entitlements.daily-cron:0 5 0 * * *}", zone = "Africa/Nouakchott")
    @SchedulerLock(name = "entitlements-daily", lockAtLeastFor = "PT1M")
    void daily() {
        DailyRun run = runDaily(LocalDate.now(EntitlementEngine.ZONE));
        log.info("Tâche quotidienne des droits : {} contrat(s) activé(s), {} expiré(s), {} erreur(s)",
                run.activated(), run.expired(), run.errors().size());
    }

    /** Chaque contrat est traité dans sa propre transaction : une erreur n'arrête pas les autres. */
    DailyRun runDaily(LocalDate today) {
        List<String> errors = new ArrayList<>();
        int activated = 0;
        for (UUID id : engine.contractsAwaitingActivation()) {
            try {
                engine.activateContract(id);
                activated++;
            } catch (RuntimeException e) {
                log.warn("Activation du contrat {} impossible", id, e);
                errors.add(id + " : " + e.getMessage());
            }
        }
        int expired = 0;
        for (UUID id : engine.contractsEndedBefore(today)) {
            try {
                engine.expireContract(id);
                expired++;
            } catch (RuntimeException e) {
                log.warn("Expiration du contrat {} impossible", id, e);
                errors.add(id + " : " + e.getMessage());
            }
        }
        return new DailyRun(activated, expired, errors);
    }

    record DailyRun(int activated, int expired, List<String> errors) {
    }
}
