package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.catalog.PaymentCatalog;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.entitlement.EntitlementEngine;
import mr.epayment.portal.entitlement.PaymentAuthorization;
import mr.epayment.portal.gateway.PaymentGateway;
import mr.epayment.portal.gateway.PaymentProvider;
import mr.epayment.portal.gateway.PaymentProvider.PaymentRequest;
import mr.epayment.portal.gateway.PaymentProvider.ProviderResult;
import mr.epayment.portal.gateway.PaymentProvider.ProviderUnavailableException;
import mr.epayment.portal.gateway.ProviderWebhook;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.shared.PageResponse;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paiements des abonnés.
 * <ol>
 *   <li>Idempotence : la même clé (Idempotency-Key) renvoie toujours la même transaction, jamais un second débit.</li>
 *   <li>Contrôle des droits côté serveur (moteur des droits), plafond mensuel compris.</li>
 *   <li>Transaction enregistrée, puis appel du fournisseur hors transaction de base de données.</li>
 *   <li>Mise à jour par la réponse, le webhook signé ou la consultation du statut ; une transaction terminée
 *       ne change plus.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");

    private final TransactionRepository transactions;
    private final CurrentSubscriber currentSubscriber;
    private final SubscriberService subscribers;
    private final ReceiptService receipts;
    private final EntitlementEngine entitlements;
    private final PaymentCatalog catalog;
    private final PaymentGateway gateway;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate inTransaction;
    private final String portalUrl;

    PaymentService(TransactionRepository transactions, CurrentSubscriber currentSubscriber,
            SubscriberService subscribers, ReceiptService receipts, EntitlementEngine entitlements,
            PaymentCatalog catalog, PaymentGateway gateway, AuditService auditService, ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager, @Value("${epayment.portal-url}") String portalUrl) {
        this.transactions = transactions;
        this.currentSubscriber = currentSubscriber;
        this.subscribers = subscribers;
        this.receipts = receipts;
        this.entitlements = entitlements;
        this.catalog = catalog;
        this.gateway = gateway;
        this.auditService = auditService;
        this.events = events;
        this.inTransaction = new TransactionTemplate(transactionManager);
        this.portalUrl = portalUrl;
    }

    public TransactionView pay(Authentication authentication, PayCommand command) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        Optional<PaymentTransaction> replay = transactions.findBySubscriberIdAndIdempotencyKey(subscriber.id(),
                command.idempotencyKey());
        if (replay.isPresent()) {
            return view(replay.get());
        }

        PaymentMethodView method = catalogByCode().get(command.methodCode());
        BigDecimal engaged = method == null ? BigDecimal.ZERO
                : transactions.engagedSince(subscriber.id(), method.id(),
                        LocalDate.now(ZONE).withDayOfMonth(1).atStartOfDay(ZONE).toInstant());
        PaymentAuthorization authorization = entitlements.authorize(subscriber.id(), command.methodCode(),
                command.amount(), engaged);
        if (!authorization.allowed()) {
            audit(authentication, "PAYMENT_REFUSED", null, Outcome.FAILURE, Map.of("method", command.methodCode(),
                    "amount", command.amount(), "refusal", authorization.refusal().name()));
            throw new PaymentRefusedException(authorization.refusal(), authorization.message());
        }
        boolean wallet = "WALLET".equals(authorization.category());
        if (wallet && command.payerPhone() == null) {
            throw new FieldValidationException(Map.of("payerPhone",
                    "Indiquez le numéro de téléphone du wallet " + authorization.methodName() + "."));
        }

        // 1. Enregistrement (CREATED) : la référence existe avant tout appel au fournisseur.
        PaymentTransaction created;
        try {
            created = inTransaction.execute(status -> transactions.saveAndFlush(new PaymentTransaction(
                    "TRX-%06d".formatted(transactions.nextReferenceNumber()), subscriber.id(),
                    authorization.paymentMethodId(), authorization.contractId(), command.amount(),
                    authorization.currency(), command.idempotencyKey(), wallet ? command.payerPhone() : null,
                    command.description())));
        } catch (DataIntegrityViolationException e) {
            // Même clé envoyée deux fois en parallèle : on renvoie celle qui a été enregistrée.
            return transactions.findBySubscriberIdAndIdempotencyKey(subscriber.id(), command.idempotencyKey())
                    .map(this::view).orElseThrow(() -> e);
        }

        // 2. Fournisseur, hors transaction de base de données (appel réseau).
        ProviderResult result = null;
        try {
            String back = portalUrl + "/abonne/paiement/retour?ref=" + created.getReference();
            result = gateway.initiate(authorization.providerCode(), new PaymentRequest(created.getReference(),
                    command.amount(), authorization.currency(), created.getPayerPhone(), command.description(), back,
                    back + "&annule=1"));
        } catch (ProviderUnavailableException e) {
            log.warn("Paiement {} : {}", created.getReference(), e.getMessage());
        }

        // 3. Résultat de l'initiation (un webhook déjà arrivé l'emporte : la transaction terminée ne bouge plus).
        ProviderResult initiation = result;
        PaymentTransaction updated = update(created.getId(), tx -> {
            if (initiation == null) {
                tx.markProviderUnavailable();
            } else {
                tx.applyProviderUpdate(initiation.outcome(), initiation.providerReference(),
                        initiation.providerStatus(), initiation.failureCode(), initiation.maskedPan(),
                        initiation.redirectUrl());
            }
        });
        audit(authentication, "PAYMENT_INITIATED", updated.getReference(),
                updated.getStatus() == TransactionStatus.FAILED ? Outcome.FAILURE : Outcome.SUCCESS,
                details(updated, authorization.methodName()));
        return view(updated);
    }

    /**
     * Transaction de l'abonné connecté. Avec {@code refresh}, le statut est redemandé au fournisseur (échange
     * serveur à serveur authentifié) : utile au retour de la page carte, avant l'arrivée du webhook.
     */
    public TransactionView get(Authentication authentication, String reference, boolean refresh) {
        PaymentTransaction tx = own(authentication, reference);
        if (refresh && !tx.getStatus().isFinal() && tx.getProviderReference() != null) {
            tx = refreshFromProvider(tx);
        }
        return view(tx);
    }

    /** Abandon d'un paiement carte : seulement si la passerelle confirme qu'il n'a pas été payé. */
    public TransactionView cancel(Authentication authentication, String reference) {
        PaymentTransaction tx = own(authentication, reference);
        if (tx.getStatus().isFinal()) {
            return view(tx);
        }
        tx = refreshFromProvider(tx);
        if (!tx.getStatus().isFinal()) {
            tx = update(tx.getId(), PaymentTransaction::cancel);
            audit(authentication, "PAYMENT_CANCELLED", reference, Outcome.SUCCESS, Map.of());
        }
        return view(tx);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionView> history(Authentication authentication, Filter filter, int page, int size) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        return search(filter.withSubscriber(subscriber.id()), page, size);
    }

    /** Historique de toutes les transactions (administrateur), filtrable par abonné. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionView> search(Filter filter, int page, int size) {
        return searchUpTo(filter, page, Math.clamp(size, 1, 100));
    }

    /** Nombre de transactions et montant encaissé sur les filtres actifs (toutes pages confondues). */
    @Transactional(readOnly = true)
    public FilteredTotals totals(Filter filter) {
        Criteria c = criteria(filter);
        return transactions.totals(c.subscriberId(), c.status(), c.methodId(), c.from(), c.to(), c.reference());
    }

    /** Totaux de l'historique de l'abonné connecté. */
    @Transactional(readOnly = true)
    public FilteredTotals historyTotals(Authentication authentication, Filter filter) {
        return totals(filter.withSubscriber(currentSubscriber.require(authentication).id()));
    }

    /** Export CSV (séparateur « ; », lisible par Excel en français) des transactions filtrées, 5 000 au plus. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Filter filter, boolean withSubscriber) {
        var rows = searchUpTo(filter, 0, 5000).content();
        return TransactionCsv.write(rows, withSubscriber);
    }

    /** Export CSV de l'historique de l'abonné connecté. */
    @Transactional(readOnly = true)
    public byte[] exportHistoryCsv(Authentication authentication, Filter filter) {
        return exportCsv(filter.withSubscriber(currentSubscriber.require(authentication).id()), false);
    }

    private PageResponse<TransactionView> searchUpTo(Filter filter, int page, int size) {
        Criteria c = criteria(filter);
        var result = transactions.search(c.subscriberId(), c.status(), c.methodId(), c.from(), c.to(), c.reference(),
                PageRequest.of(Math.max(page, 0), size));
        Map<UUID, PaymentMethodView> methods = catalogById();
        Map<UUID, SubscriberView> parties = subscribers.findAllById(
                result.getContent().stream().map(PaymentTransaction::getSubscriberId).collect(Collectors.toSet()));
        return PageResponse.of(result, tx -> view(tx, methods, parties.get(tx.getSubscriberId())));
    }

    /** Filtres de l'API traduits pour les requêtes (dates de Nouakchott, référence en motif LIKE). */
    private Criteria criteria(Filter filter) {
        // Moyen inconnu : identifiant impossible, donc aucun résultat (plutôt que tout afficher).
        UUID methodId = filter.methodCode() == null ? null
                : Optional.ofNullable(catalogByCode().get(filter.methodCode())).map(PaymentMethodView::id)
                        .orElse(new UUID(0, 0));
        String reference = filter.reference() == null || filter.reference().isBlank() ? null
                : "%" + filter.reference().trim().toLowerCase(Locale.ROOT) + "%";
        return new Criteria(filter.subscriberId(), filter.status(), methodId,
                filter.from() == null ? null : filter.from().atStartOfDay(ZONE).toInstant(),
                filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(ZONE).toInstant(), reference);
    }

    private record Criteria(UUID subscriberId, TransactionStatus status, UUID methodId, Instant from, Instant to,
            String reference) {
    }

    /** Reçu PDF d'un paiement réussi de l'abonné connecté. */
    public Receipt receipt(Authentication authentication, String reference) {
        return receiptOf(own(authentication, reference));
    }

    /** Reçu PDF de n'importe quel paiement réussi (administrateur). */
    public Receipt adminReceipt(String reference) {
        return receiptOf(find(reference));
    }

    @Transactional(readOnly = true)
    public TransactionView adminGet(String reference) {
        return view(find(reference));
    }

    private Receipt receiptOf(PaymentTransaction tx) {
        byte[] pdf = receipts.receipt(tx, catalogById().get(tx.getPaymentMethodId()));
        return new Receipt("recu-" + tx.getReference() + ".pdf", pdf);
    }

    private PaymentTransaction find(String reference) {
        return transactions.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Transaction introuvable."));
    }

    /**
     * Webhook signé et nouveau (anti-rejeu déjà fait par le module gateway), dans la même transaction que son
     * enregistrement : le webhook et la mise à jour du paiement sont validés ensemble.
     */
    @EventListener
    void onProviderWebhook(ProviderWebhook webhook) {
        Optional<PaymentTransaction> found = Optional.ofNullable(webhook.merchantReference())
                .flatMap(transactions::findByReference);
        if (found.isEmpty()) {
            audit(null, "WEBHOOK_UNMATCHED", webhook.merchantReference(), Outcome.FAILURE,
                    Map.of("provider", webhook.providerCode(), "eventId", webhook.eventId()));
            return;
        }
        PaymentTransaction tx = found.get();
        PaymentMethodView method = catalogById().get(tx.getPaymentMethodId());
        if (!method.provider().code().equals(webhook.providerCode())
                || (webhook.amount() != null && webhook.amount().compareTo(tx.getAmount()) != 0)) {
            // Fournisseur ou montant incohérent : on ne touche pas au paiement, l'anomalie est tracée.
            audit(null, "WEBHOOK_ANOMALY", tx.getReference(), Outcome.FAILURE, Map.of(
                    "provider", webhook.providerCode(), "expectedAmount", tx.getAmount(),
                    "receivedAmount", String.valueOf(webhook.amount())));
            return;
        }
        TransactionStatus before = tx.getStatus();
        if (tx.applyProviderUpdate(webhook.outcome(), webhook.providerReference(), webhook.providerStatus(),
                webhook.failureCode(), webhook.maskedPan(), null)) {
            audit(null, "PAYMENT_UPDATED", tx.getReference(), Outcome.SUCCESS, Map.of(
                    "source", "WEBHOOK", "status", tx.getStatus().name(), "providerStatus", webhook.providerStatus()));
            publishIfCompleted(before, tx, method);
        } else if (before.isFinal() && !sameOutcome(before, webhook.outcome())) {
            // Ex. succès annoncé après la clôture : la transaction ne change pas, mais un humain doit vérifier.
            audit(null, "WEBHOOK_CONFLICT", tx.getReference(), Outcome.FAILURE, Map.of(
                    "status", before.name(), "providerOutcome", webhook.outcome().name(),
                    "providerStatus", webhook.providerStatus()));
        }
    }

    /**
     * Rapprochement : paiements toujours en cours (webhook perdu, abonné parti avant la fin).
     * Avec une référence fournisseur, le statut est redemandé ; sans référence au bout de 10 minutes,
     * l'envoi au fournisseur n'a jamais abouti et la transaction est close.
     *
     * @return nombre de transactions mises à jour
     */
    public int reconcileUnfinished(Instant startedBefore) {
        int updated = 0;
        for (PaymentTransaction tx : transactions.findUnfinishedCreatedBefore(startedBefore)) {
            TransactionStatus before = tx.getStatus();
            PaymentTransaction after;
            if (tx.getProviderReference() != null) {
                after = refreshFromProvider(tx);
            } else if (tx.getCreatedAt().isBefore(Instant.now().minus(Duration.ofMinutes(10)))) {
                after = update(tx.getId(), PaymentTransaction::markProviderUnavailable);
            } else {
                continue;
            }
            if (after.getStatus() != before) {
                updated++;
                audit(null, "PAYMENT_UPDATED", tx.getReference(), Outcome.SUCCESS, Map.of(
                        "source", "RECONCILIATION", "status", after.getStatus().name(),
                        "providerStatus", String.valueOf(after.getProviderStatus())));
            }
        }
        return updated;
    }

    private static boolean sameOutcome(TransactionStatus status, PaymentProvider.Outcome outcome) {
        return switch (outcome) {
            case SUCCEEDED -> status == TransactionStatus.SUCCEEDED;
            case FAILED -> status == TransactionStatus.FAILED;
            case CANCELLED -> status == TransactionStatus.CANCELLED;
            case PENDING -> false;
        };
    }

    private PaymentTransaction refreshFromProvider(PaymentTransaction tx) {
        PaymentMethodView method = catalogById().get(tx.getPaymentMethodId());
        try {
            ProviderResult status = gateway.fetchStatus(method.provider().code(), tx.getProviderReference());
            return update(tx.getId(), t -> t.applyProviderUpdate(status.outcome(), null, status.providerStatus(),
                    status.failureCode(), status.maskedPan(), null));
        } catch (ProviderUnavailableException e) {
            log.warn("Statut de {} indisponible : {}", tx.getReference(), e.getMessage());
            return tx;
        }
    }

    private PaymentTransaction update(UUID id, Consumer<PaymentTransaction> change) {
        try {
            return inTransaction.execute(status -> {
                PaymentTransaction tx = transactions.findById(id).orElseThrow();
                TransactionStatus before = tx.getStatus();
                change.accept(tx);
                PaymentTransaction saved = transactions.saveAndFlush(tx);
                publishIfCompleted(before, saved, catalogById().get(saved.getPaymentMethodId()));
                return saved;
            });
        } catch (OptimisticLockingFailureException e) {
            // Mise à jour concurrente (webhook arrivé au même instant) : on relit l'état qui a gagné.
            return transactions.findById(id).orElseThrow();
        }
    }

    /** Publie la fin du paiement (dans la transaction en cours : les écouteurs agissent après validation). */
    private void publishIfCompleted(TransactionStatus before, PaymentTransaction tx, PaymentMethodView method) {
        boolean completed = tx.getStatus() == TransactionStatus.SUCCEEDED || tx.getStatus() == TransactionStatus.FAILED;
        if (before.isFinal() || !completed) {
            return;
        }
        SubscriberView subscriber = subscribers.get(tx.getSubscriberId());
        events.publishEvent(new PaymentCompleted(tx.getReference(), subscriber.id(), subscriber.email(),
                subscriber.displayName(), tx.getStatus() == TransactionStatus.SUCCEEDED, method.name(),
                tx.getAmount(), tx.getCurrency(), tx.getFailureMessage(), tx.getCompletedAt()));
    }

    private PaymentTransaction own(Authentication authentication, String reference) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        return transactions.findByReference(reference)
                .filter(tx -> tx.getSubscriberId().equals(subscriber.id()))
                .orElseThrow(() -> new NotFoundException("Transaction introuvable."));
    }

    private TransactionView view(PaymentTransaction tx) {
        return view(tx, catalogById(), subscribers.get(tx.getSubscriberId()));
    }

    private static TransactionView view(PaymentTransaction tx, Map<UUID, PaymentMethodView> methods,
            SubscriberView subscriber) {
        PaymentMethodView m = methods.get(tx.getPaymentMethodId());
        return new TransactionView(tx.getReference(), tx.getStatus(), tx.getProviderStatus(),
                tx.getProviderReference(), m.code(), m.name(), m.category(), m.shortLabel(), m.color(), m.logoUrl(),
                tx.getAmount(), tx.getCurrency(), tx.getDescription(), tx.getPayerPhone(), tx.getCardMaskedPan(),
                tx.getFailureCode(), tx.getFailureMessage(), tx.getRedirectUrl(), tx.getContractId(),
                tx.getSubscriberId(), subscriber.reference(), subscriber.displayName(), tx.getCreatedAt(),
                tx.getCompletedAt());
    }

    private Map<String, PaymentMethodView> catalogByCode() {
        return catalog.findAll().stream().collect(Collectors.toMap(PaymentMethodView::code, Function.identity()));
    }

    private Map<UUID, PaymentMethodView> catalogById() {
        return catalog.findAll().stream().collect(Collectors.toMap(PaymentMethodView::id, Function.identity()));
    }

    private static Map<String, Object> details(PaymentTransaction tx, String methodName) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", methodName);
        details.put("amount", tx.getAmount());
        details.put("currency", tx.getCurrency());
        details.put("status", tx.getStatus().name());
        details.put("contractId", tx.getContractId().toString());
        if (tx.getProviderReference() != null) {
            details.put("providerReference", tx.getProviderReference());
        }
        return details;
    }

    private void audit(Authentication authentication, String action, String reference, Outcome outcome,
            Map<String, Object> details) {
        auditService.record(AuditEvent.of(authentication, action, "TRANSACTION", reference, outcome, details));
    }

    /** Filtres de l'historique ; tous facultatifs (dates incluses, heure de Nouakchott). */
    public record Filter(UUID subscriberId, TransactionStatus status, String methodCode, LocalDate from, LocalDate to,
            String reference) {

        Filter withSubscriber(UUID id) {
            return new Filter(id, status, methodCode, from, to, reference);
        }
    }

    public record Receipt(String fileName, byte[] content) {
    }

    /** Demande de paiement de l'abonné ; la clé d'idempotence est générée par le navigateur pour chaque tentative. */
    public record PayCommand(String methodCode, BigDecimal amount, String payerPhone, String description,
            String idempotencyKey) {
    }
}
