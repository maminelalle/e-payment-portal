package mr.epayment.simulator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mr.epayment.simulator.SimPayment.State;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cycle de vie des paiements simulés : création idempotente, issue selon le scénario après un délai,
 * puis webhook signé vers le portail. Tout est en mémoire : un redémarrage efface les paiements.
 */
@Service
public class PaymentEngine {

    private final Map<String, SimPayment> payments = new ConcurrentHashMap<>();
    private final Map<String, Idempotent> idempotencyKeys = new ConcurrentHashMap<>();
    private final TaskScheduler scheduler;
    private final WebhookSender webhooks;
    private final SimulatorProperties properties;

    PaymentEngine(TaskScheduler scheduler, WebhookSender webhooks, SimulatorProperties properties) {
        this.scheduler = scheduler;
        this.webhooks = webhooks;
        this.properties = properties;
    }

    /**
     * Même clé d'idempotence + même demande : on renvoie le paiement déjà créé (aucun second débit).
     * Même clé + demande différente : conflit.
     */
    public SimPayment create(NewPayment request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return register(request);
        }
        String key = request.provider() + ":" + idempotencyKey;
        Idempotent existing = idempotencyKeys.computeIfAbsent(key,
                k -> new Idempotent(request.fingerprint(), register(request).id()));
        if (!existing.fingerprint().equals(request.fingerprint())) {
            throw new IdempotencyConflictException();
        }
        return payments.get(existing.paymentId());
    }

    public Optional<SimPayment> find(ProviderCode provider, String id) {
        return Optional.ofNullable(payments.get(id)).filter(p -> p.provider() == provider)
                .map(p -> {
                    if (p.expireIfDue(Instant.now())) {
                        webhooks.send(p);
                    }
                    return p;
                });
    }

    public Collection<SimPayment> all() {
        return payments.values();
    }

    /** Carte saisie sur la page hébergée : le paiement suit maintenant le scénario de la carte de test. */
    public void cardSubmitted(SimPayment payment, String maskedPan, String cardToken, Scenario scenario) {
        payment.cardSubmitted(maskedPan, cardToken, scenario);
        scheduleOutcome(payment);
    }

    /** Sessions carte non payées à temps → expirées, et le portail est prévenu. */
    @Scheduled(fixedDelay = 30_000)
    void expireOpenSessions() {
        Instant now = Instant.now();
        payments.values().stream().filter(p -> p.expireIfDue(now)).forEach(webhooks::send);
    }

    private SimPayment register(NewPayment request) {
        boolean card = request.provider() == ProviderCode.VISA;
        SimPayment payment = new SimPayment(request.provider(), request.newId(), request.merchantReference(),
                request.amount(), request.currency(), request.payerPhone(), request.description(), request.returnUrl(),
                request.cancelUrl(), request.scenario(), card ? State.OPEN : State.PENDING,
                card ? Instant.now().plus(properties.checkoutSessionValidity()) : null);
        payments.put(payment.id(), payment);
        if (!card) {
            // Wallet : l'abonné « confirme sur son téléphone » pendant le délai.
            scheduleOutcome(payment);
        }
        return payment;
    }

    private void scheduleOutcome(SimPayment payment) {
        Scenario scenario = payment.scenario();
        Duration delay = scenario == Scenario.DELAYED ? properties.delayedConfirmDelay() : properties.confirmDelay();
        scheduler.schedule(() -> {
            switch (scenario) {
                case FAILED -> payment.resolve(State.FAILED, "DECLINED");
                case INSUFFICIENT_FUNDS -> payment.resolve(State.FAILED, "INSUFFICIENT_FUNDS");
                default -> payment.resolve(State.SUCCEEDED, null);
            }
            if (scenario != Scenario.NO_WEBHOOK) {
                webhooks.send(payment);
            }
        }, Instant.now().plus(delay));
    }

    /** Demande de paiement reçue par l'une des API. */
    public record NewPayment(ProviderCode provider, String idPrefix, String merchantReference, BigDecimal amount,
            String currency, String payerPhone, String description, String returnUrl, String cancelUrl,
            Scenario scenario) {

        String fingerprint() {
            return String.join("|", merchantReference, amount.toPlainString(), currency, String.valueOf(payerPhone),
                    String.valueOf(returnUrl));
        }

        String newId() {
            return idPrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }

    private record Idempotent(String fingerprint, String paymentId) {
    }

    /** Même clé d'idempotence, demande différente → HTTP 409. */
    public static class IdempotencyConflictException extends RuntimeException {
        IdempotencyConflictException() {
            super("Idempotency-Key déjà utilisée pour une autre demande.");
        }
    }
}
