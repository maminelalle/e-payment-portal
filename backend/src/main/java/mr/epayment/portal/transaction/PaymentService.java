package mr.epayment.portal.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
import mr.epayment.portal.gateway.PaymentProvider.PaymentRequest;
import mr.epayment.portal.gateway.PaymentProvider.ProviderResult;
import mr.epayment.portal.gateway.PaymentProvider.ProviderUnavailableException;
import mr.epayment.portal.gateway.ProviderWebhook;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.shared.PageResponse;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import mr.epayment.portal.subscriber.SubscriberView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final EntitlementEngine entitlements;
    private final PaymentCatalog catalog;
    private final PaymentGateway gateway;
    private final AuditService auditService;
    private final TransactionTemplate inTransaction;
    private final String portalUrl;

    PaymentService(TransactionRepository transactions, CurrentSubscriber currentSubscriber,
            EntitlementEngine entitlements, PaymentCatalog catalog, PaymentGateway gateway, AuditService auditService,
            PlatformTransactionManager transactionManager, @Value("${epayment.portal-url}") String portalUrl) {
        this.transactions = transactions;
        this.currentSubscriber = currentSubscriber;
        this.entitlements = entitlements;
        this.catalog = catalog;
        this.gateway = gateway;
        this.auditService = auditService;
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
    public PageResponse<TransactionView> history(Authentication authentication, int page, int size) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        Map<UUID, PaymentMethodView> methods = catalogById();
        return PageResponse.of(transactions.findBySubscriberIdOrderByCreatedAtDesc(subscriber.id(), pageable),
                tx -> view(tx, methods));
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
        if (tx.applyProviderUpdate(webhook.outcome(), webhook.providerReference(), webhook.providerStatus(),
                webhook.failureCode(), webhook.maskedPan(), null)) {
            audit(null, "PAYMENT_UPDATED", tx.getReference(), Outcome.SUCCESS, Map.of(
                    "source", "WEBHOOK", "status", tx.getStatus().name(), "providerStatus", webhook.providerStatus()));
        }
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
                change.accept(tx);
                return transactions.saveAndFlush(tx);
            });
        } catch (OptimisticLockingFailureException e) {
            // Mise à jour concurrente (webhook arrivé au même instant) : on relit l'état qui a gagné.
            return transactions.findById(id).orElseThrow();
        }
    }

    private PaymentTransaction own(Authentication authentication, String reference) {
        SubscriberView subscriber = currentSubscriber.require(authentication);
        return transactions.findByReference(reference)
                .filter(tx -> tx.getSubscriberId().equals(subscriber.id()))
                .orElseThrow(() -> new NotFoundException("Transaction introuvable."));
    }

    private TransactionView view(PaymentTransaction tx) {
        return view(tx, catalogById());
    }

    private static TransactionView view(PaymentTransaction tx, Map<UUID, PaymentMethodView> methods) {
        PaymentMethodView m = methods.get(tx.getPaymentMethodId());
        return new TransactionView(tx.getReference(), tx.getStatus(), tx.getProviderStatus(), m.code(), m.name(),
                m.category(), m.shortLabel(), m.color(), m.logoUrl(), tx.getAmount(), tx.getCurrency(),
                tx.getDescription(), tx.getPayerPhone(), tx.getCardMaskedPan(), tx.getFailureCode(),
                tx.getFailureMessage(), tx.getRedirectUrl(), tx.getContractId(), tx.getCreatedAt(),
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

    /** Demande de paiement de l'abonné ; la clé d'idempotence est générée par le navigateur pour chaque tentative. */
    public record PayCommand(String methodCode, BigDecimal amount, String payerPhone, String description,
            String idempotencyKey) {
    }
}
