package mr.epayment.portal.entitlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.catalog.PaymentCatalog;
import mr.epayment.portal.catalog.PaymentMethodStatus;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.contract.ContractService;
import mr.epayment.portal.contract.ContractView;
import mr.epayment.portal.entitlement.Entitlement.Status;
import mr.epayment.portal.entitlement.PaymentAuthorization.Refusal;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberStatus;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moteur des droits : le contrat signé et actif est la seule source des droits.
 * <ul>
 *   <li>activation : un droit par moyen inscrit au contrat, période et plafonds du contrat (idempotent) ;</li>
 *   <li>suspension, reprise, résiliation, expiration : le contrat et ses droits changent ensemble ;</li>
 *   <li>contrôle : chaque paiement est autorisé ici, côté serveur, quel que soit l'affichage.</li>
 * </ul>
 * Les droits ne sont jamais supprimés : l'historique des transactions garde son contrat d'origine.
 */
@Service
public class EntitlementEngine {

    static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");

    private final EntitlementRepository entitlements;
    private final ContractService contracts;
    private final SubscriberService subscribers;
    private final PaymentCatalog catalog;
    private final AuditService auditService;

    EntitlementEngine(EntitlementRepository entitlements, ContractService contracts, SubscriberService subscribers,
            PaymentCatalog catalog, AuditService auditService) {
        this.entitlements = entitlements;
        this.contracts = contracts;
        this.subscribers = subscribers;
        this.catalog = catalog;
        this.auditService = auditService;
    }

    /**
     * Signature vérifiée → contrat actif → un droit par moyen inscrit. Rejouable sans effet de bord.
     * Toujours dans sa propre transaction : appelée aussi après la validation de celle de la signature.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ContractView activateContract(UUID contractId) {
        ContractView contract = contracts.activate(contractId);
        Map<String, PaymentMethodView> byCode = catalogByCode();
        int created = 0;
        for (ContractView.Method method : contract.methods()) {
            created += entitlements.insertIfAbsent(UUID.randomUUID(), contract.subscriber().id(), contractId,
                    byCode.get(method.code()).id(), contract.startDate(), contract.endDate(),
                    method.perTransactionLimit(), method.monthlyLimit());
        }
        subscribers.activateIfPending(contract.subscriber().id());
        if (created > 0) {
            audit("ENTITLEMENTS_OPENED", contract, Map.of("created", created, "methods",
                    contract.methods().stream().map(ContractView.Method::code).toList()));
        }
        return contract;
    }

    @Transactional
    public ContractView suspendContract(UUID contractId, String reason) {
        ContractView contract = contracts.suspend(contractId, reason);
        moveEntitlements(contract, Status.ACTIVE, Status.SUSPENDED, reason, "ENTITLEMENTS_SUSPENDED");
        return contract;
    }

    @Transactional
    public ContractView resumeContract(UUID contractId) {
        ContractView contract = contracts.resume(contractId);
        moveEntitlements(contract, Status.SUSPENDED, Status.ACTIVE, null, "ENTITLEMENTS_RESUMED");
        return contract;
    }

    @Transactional
    public ContractView terminateContract(UUID contractId, String reason) {
        ContractView contract = contracts.terminate(contractId, reason);
        moveEntitlements(contract, null, Status.REVOKED, reason == null ? "Résiliation" : reason,
                "ENTITLEMENTS_REVOKED");
        return contract;
    }

    @Transactional
    public ContractView expireContract(UUID contractId) {
        ContractView contract = contracts.expire(contractId);
        moveEntitlements(contract, null, Status.EXPIRED, "Fin de validité du contrat", "ENTITLEMENTS_EXPIRED");
        return contract;
    }

    /**
     * Contrôle serveur avant chaque paiement.
     *
     * @param monthToDate montant déjà payé ce mois-ci avec ce moyen (transactions réussies)
     */
    @Transactional(readOnly = true)
    public PaymentAuthorization authorize(UUID subscriberId, String methodCode, BigDecimal amount,
            BigDecimal monthToDate) {
        SubscriberView subscriber = subscribers.get(subscriberId);
        if (subscriber.status() != SubscriberStatus.ACTIVE) {
            return PaymentAuthorization.refused(Refusal.SUBSCRIBER_BLOCKED,
                    "Votre compte n'est pas actif : les paiements sont bloqués.");
        }
        PaymentMethodView method = catalogByCode().get(methodCode);
        if (method == null) {
            return PaymentAuthorization.refused(Refusal.NOT_ENTITLED, "Moyen de paiement inconnu.");
        }
        Entitlement entitlement = entitlements.findBySubscriberIdAndStatus(subscriberId, Status.ACTIVE).stream()
                .filter(e -> e.getPaymentMethodId().equals(method.id()))
                .findFirst()
                .orElse(null);
        if (entitlement == null) {
            return PaymentAuthorization.refused(Refusal.NOT_ENTITLED,
                    method.name() + " n'est pas prévu par votre contrat actif.");
        }
        if (!entitlement.coversDate(LocalDate.now(ZONE))) {
            return PaymentAuthorization.refused(Refusal.OUTSIDE_VALIDITY,
                    "Votre contrat n'est pas en vigueur aujourd'hui (du " + entitlement.getValidFrom() + " au "
                            + entitlement.getValidTo() + ").");
        }
        if (method.status() != PaymentMethodStatus.ACTIVE) {
            return PaymentAuthorization.refused(Refusal.METHOD_DISABLED,
                    method.name() + " est momentanément indisponible.");
        }
        if (amount.compareTo(entitlement.getPerTransactionLimit()) > 0) {
            return PaymentAuthorization.refused(Refusal.OVER_TRANSACTION_LIMIT,
                    "Le montant dépasse le plafond par transaction de " + method.name() + ".");
        }
        if (monthToDate.add(amount).compareTo(entitlement.getMonthlyLimit()) > 0) {
            return PaymentAuthorization.refused(Refusal.OVER_MONTHLY_LIMIT,
                    "Le montant dépasse le plafond mensuel restant de " + method.name() + ".");
        }
        return new PaymentAuthorization(true, null, null, entitlement.getContractId(), method.id(), method.name(),
                method.category(), method.provider().code(), method.currency(), entitlement.getPerTransactionLimit(),
                entitlement.getMonthlyLimit());
    }

    /** « Mes moyens de paiement » : uniquement les droits ouverts par le contrat en vigueur. */
    @Transactional(readOnly = true)
    public List<EntitledMethod> methodsOf(UUID subscriberId) {
        SubscriberView subscriber = subscribers.get(subscriberId);
        Map<UUID, PaymentMethodView> methods = catalogById();
        return entitlements.findBySubscriberIdAndStatus(subscriberId, Status.ACTIVE).stream()
                .map(e -> view(e, methods.get(e.getPaymentMethodId()), subscriber))
                .toList();
    }

    /** Droits d'un contrat, quel que soit leur statut (fiche contrat de l'admin). */
    @Transactional(readOnly = true)
    public List<EntitledMethod> entitlementsOfContract(UUID contractId) {
        Map<UUID, PaymentMethodView> methods = catalogById();
        return entitlements.findByContractIdOrderByActivatedAt(contractId).stream()
                .map(e -> view(e, methods.get(e.getPaymentMethodId()), null))
                .toList();
    }

    /** Contrats signés pas encore activés (rattrapage par la tâche quotidienne). */
    public List<UUID> contractsAwaitingActivation() {
        return contracts.signedAwaitingActivation();
    }

    /** Contrats en vigueur dont la date de fin est dépassée. */
    public List<UUID> contractsEndedBefore(LocalDate today) {
        return contracts.inForceEndedBefore(today);
    }

    private void moveEntitlements(ContractView contract, Status from, Status to, String reason, String action) {
        int moved = 0;
        for (Entitlement entitlement : entitlements.findByContractIdOrderByActivatedAt(contract.id())) {
            boolean eligible = from == null
                    ? entitlement.getStatus() == Status.ACTIVE || entitlement.getStatus() == Status.SUSPENDED
                    : entitlement.getStatus() == from;
            if (eligible && entitlement.changeStatus(to, reason)) {
                moved++;
            }
        }
        if (moved > 0) {
            audit(action, contract, reason == null ? Map.of("count", moved) : Map.of("count", moved, "reason", reason));
        }
    }

    private EntitledMethod view(Entitlement e, PaymentMethodView method, SubscriberView subscriber) {
        String reason = null;
        if (e.getStatus() != Status.ACTIVE) {
            reason = "Droit " + e.getStatus().name().toLowerCase();
        } else if (subscriber != null && subscriber.status() != SubscriberStatus.ACTIVE) {
            reason = "Compte non actif";
        } else if (!e.coversDate(LocalDate.now(ZONE))) {
            reason = LocalDate.now(ZONE).isBefore(e.getValidFrom()) ? "Contrat pas encore en vigueur" : "Contrat échu";
        } else if (method.status() != PaymentMethodStatus.ACTIVE) {
            reason = "Momentanément indisponible";
        }
        return new EntitledMethod(e.getId(), e.getContractId(), method.code(), method.name(), method.category(),
                method.shortLabel(), method.color(), method.logoUrl(), method.description(), method.instructions(),
                method.conditions(), method.currency(),
                e.getPerTransactionLimit(), e.getMonthlyLimit(), e.getValidFrom(), e.getValidTo(),
                e.getStatus().name(), reason == null, reason);
    }

    private Map<String, PaymentMethodView> catalogByCode() {
        return catalog.findAll().stream().collect(Collectors.toMap(PaymentMethodView::code, Function.identity()));
    }

    private Map<UUID, PaymentMethodView> catalogById() {
        return catalog.findAll().stream().collect(Collectors.toMap(PaymentMethodView::id, Function.identity()));
    }

    private void audit(String action, ContractView contract, Map<String, Object> details) {
        Map<String, Object> all = new LinkedHashMap<>(details);
        all.put("contract", contract.reference());
        all.put("subscriber", contract.subscriber().reference());
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(), action,
                "CONTRACT", contract.id().toString(), Outcome.SUCCESS, all));
    }
}
