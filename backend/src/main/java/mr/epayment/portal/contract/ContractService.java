package mr.epayment.portal.contract;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import mr.epayment.portal.audit.AuditEvent;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import mr.epayment.portal.audit.AuditService;
import mr.epayment.portal.catalog.PaymentCatalog;
import mr.epayment.portal.catalog.PaymentMethodStatus;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.contract.ContractTemplate.TemplateView;
import mr.epayment.portal.document.DocumentStorage;
import mr.epayment.portal.document.PdfRenderer;
import mr.epayment.portal.shared.BusinessRuleException;
import mr.epayment.portal.shared.ConflictException;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.shared.PageResponse;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberStatus;
import mr.epayment.portal.subscriber.SubscriberView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrats côté administrateur : brouillon, PDF, envoi pour signature.
 * Le contrat est la seule source des droits : une fois envoyé, son contenu (moyens, plafonds, dates)
 * et son PDF sont figés ; l'empreinte SHA-256 du PDF permet de prouver qu'il n'a pas changé.
 */
@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final ContractRepository contracts;
    private final ContractTemplateRepository templates;
    private final SubscriberService subscribers;
    private final PaymentCatalog catalog;
    private final PdfRenderer pdfRenderer;
    private final DocumentStorage storage;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    ContractService(ContractRepository contracts, ContractTemplateRepository templates, SubscriberService subscribers,
            PaymentCatalog catalog, PdfRenderer pdfRenderer, DocumentStorage storage, AuditService auditService,
            ApplicationEventPublisher events) {
        this.contracts = contracts;
        this.templates = templates;
        this.subscribers = subscribers;
        this.catalog = catalog;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> activeTemplates() {
        return templates.findActive().stream().map(ContractTemplate::toView).toList();
    }

    /** Étape 1 de l'assistant : brouillon rattaché à un abonné et à un modèle ; la référence est attribuée. */
    @Transactional
    public ContractView createDraft(UUID subscriberId, UUID templateId) {
        SubscriberView subscriber = subscribers.get(subscriberId);
        if (subscriber.status() == SubscriberStatus.DISABLED) {
            throw new BusinessRuleException("L'abonné " + subscriber.displayName() + " est désactivé.");
        }
        requireActiveTemplate(templateId);
        String reference = "CTR-%d-%04d".formatted(Year.now(ContractDocument.ZONE).getValue(),
                contracts.nextReferenceNumber());
        Contract contract = contracts.save(new Contract(reference, subscriberId, templateId, actorLabel()));
        audit("CONTRACT_DRAFT_CREATED", contract, Map.of("subscriber", subscriber.reference()));
        return toView(contract);
    }

    /** Étapes 2 et 3 : moyens inclus, plafonds, dates et commission. Les moyens doivent être actifs. */
    @Transactional
    public ContractView updateDraft(UUID id, DraftChanges changes) {
        Contract contract = find(id);
        contract.requireDraft("modifier");
        requireActiveTemplate(changes.templateId());
        Map<String, PaymentMethodView> byCode = catalogByCode();

        Map<String, String> errors = new LinkedHashMap<>();
        if (changes.startDate() != null && changes.endDate() != null && changes.endDate().isBefore(changes.startDate())) {
            errors.put("endDate", "La date de fin doit suivre la date de début.");
        }
        Set<String> seen = new HashSet<>();
        for (DraftChanges.Method method : changes.methods()) {
            PaymentMethodView known = byCode.get(method.code());
            if (known == null || known.status() != PaymentMethodStatus.ACTIVE) {
                errors.put("methods", "Le moyen " + method.code() + " n'est pas disponible dans le catalogue.");
            } else if (!seen.add(method.code())) {
                errors.put("methods", "Le moyen " + known.name() + " est sélectionné deux fois.");
            } else if (method.monthlyLimit().compareTo(method.perTransactionLimit()) < 0) {
                errors.put("methods", "Plafond mensuel de " + known.name()
                        + " inférieur au plafond par transaction.");
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        List<IncludedMethod> included = changes.methods().stream()
                .map(m -> new IncludedMethod(byCode.get(m.code()).id(), m.perTransactionLimit(), m.monthlyLimit()))
                .toList();
        contract.updateDraft(changes.templateId(), changes.startDate(), changes.endDate(), changes.commissionRate(),
                included);
        audit("CONTRACT_DRAFT_UPDATED", contract, Map.of("methods", changes.methods().size()));
        return toView(contract);
    }

    @Transactional
    public void deleteDraft(UUID id) {
        Contract contract = find(id);
        contract.requireDraft("supprimer");
        contracts.delete(contract);
        audit("CONTRACT_DRAFT_DELETED", contract, Map.of());
    }

    /**
     * Étape 4 : vérifie le contrat, génère le PDF définitif, le stocke avec son empreinte et passe le contrat
     * en « en attente de signature ». L'abonné est prévenu par email après validation de la transaction.
     */
    @Transactional
    public ContractView sendForSignature(UUID id) {
        Contract contract = find(id);
        contract.requireDraft("envoyer");
        SubscriberView subscriber = subscribers.get(contract.getSubscriberId());
        checkReadyToSend(contract, subscriber);

        byte[] pdf = renderPdf(contract, subscriber, false);
        String sha256 = sha256(pdf);
        String key = "contrats/%s/%s.pdf".formatted(contract.getReference(), contract.getReference());
        storage.put(key, pdf, "application/pdf");
        contract.markSentForSignature(key, sha256);
        contracts.flush();

        audit("CONTRACT_SENT", contract, Map.of("pdfSha256", sha256, "subscriber", subscriber.reference()));
        events.publishEvent(new ContractSentForSignature(contract.getId(), contract.getReference(),
                subscriber.email(), subscriber.displayName()));
        return toView(contract);
    }

    /**
     * PDF du contrat : aperçu généré à la volée (filigrane « BROUILLON ») tant qu'il est en brouillon,
     * sinon le document envoyé, dont l'intégrité est revérifiée avant de le servir.
     */
    @Transactional(readOnly = true)
    public ContractPdf pdf(UUID id) {
        Contract contract = find(id);
        if (contract.getStatus() == ContractStatus.DRAFT) {
            SubscriberView subscriber = subscribers.get(contract.getSubscriberId());
            return new ContractPdf(contract.getReference() + "-brouillon.pdf",
                    renderPdf(contract, subscriber, true));
        }
        byte[] stored = storage.get(contract.getPdfObjectKey());
        if (!sha256(stored).equals(contract.getPdfSha256())) {
            log.error("Empreinte du PDF du contrat {} invalide : document altéré", contract.getReference());
            throw new IllegalStateException("Le document stocké ne correspond pas à son empreinte.");
        }
        return new ContractPdf(contract.getReference() + ".pdf", stored);
    }

    @Transactional(readOnly = true)
    public ContractView get(UUID id) {
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractView> search(ContractStatus status, UUID subscriberId, String reference, int page,
            int size) {
        String term = reference == null || reference.isBlank()
                ? null
                : "%" + reference.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        var result = contracts.search(status, subscriberId, term, pageable);
        Map<UUID, SubscriberView> parties = subscribers.findAllById(
                result.getContent().stream().map(Contract::getSubscriberId).collect(Collectors.toSet()));
        Map<UUID, PaymentMethodView> methods = catalogById();
        Map<UUID, TemplateView> templateViews = templateViews();
        return PageResponse.of(result, c -> toView(c, parties.get(c.getSubscriberId()), methods, templateViews));
    }

    private void checkReadyToSend(Contract contract, SubscriberView subscriber) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ContractDocument.ZONE);
        if (contract.getStartDate() == null) {
            errors.put("startDate", "La date de début est obligatoire.");
        }
        if (contract.getEndDate() == null) {
            errors.put("endDate", "La date de fin est obligatoire.");
        } else if (contract.getEndDate().isBefore(today)) {
            errors.put("endDate", "La date de fin est déjà passée.");
        }
        if (contract.getMethods().isEmpty()) {
            errors.put("methods", "Sélectionnez au moins un moyen de paiement.");
        } else {
            Map<UUID, PaymentMethodView> methods = catalogById();
            contract.getMethods().stream()
                    .map(m -> methods.get(m.paymentMethodId()))
                    .filter(m -> m.status() != PaymentMethodStatus.ACTIVE)
                    .findFirst()
                    .ifPresent(m -> errors.put("methods", m.name() + " a été désactivé dans le catalogue."));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (subscriber.status() != SubscriberStatus.PENDING && subscriber.status() != SubscriberStatus.ACTIVE) {
            throw new BusinessRuleException("L'abonné " + subscriber.displayName() + " est "
                    + subscriber.status() + " : impossible de lui envoyer un contrat.");
        }
        contracts.findBySubscriberIdAndStatusIn(subscriber.id(), ContractStatus.IN_PROGRESS).stream()
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException("L'abonné a déjà le contrat " + other.getReference() + " ("
                            + other.getStatus() + "). Un seul contrat peut être en cours à la fois.");
                });
    }

    private byte[] renderPdf(Contract contract, SubscriberView subscriber, boolean draft) {
        ContractTemplate template = templates.findById(contract.getTemplateId()).orElseThrow();
        return pdfRenderer.renderPdf(template.getContentHtml(),
                ContractDocument.variables(contract, subscriber, catalogById(), draft));
    }

    private void requireActiveTemplate(UUID templateId) {
        templates.findById(templateId)
                .filter(ContractTemplate::isActive)
                .orElseThrow(() -> new NotFoundException("Modèle de contrat introuvable ou archivé."));
    }

    private Contract find(UUID id) {
        return contracts.findById(id).orElseThrow(() -> new NotFoundException("Contrat introuvable."));
    }

    private ContractView toView(Contract contract) {
        return toView(contract, subscribers.get(contract.getSubscriberId()), catalogById(), templateViews());
    }

    private static ContractView toView(Contract c, SubscriberView subscriber, Map<UUID, PaymentMethodView> methods,
            Map<UUID, TemplateView> templateViews) {
        var party = new ContractView.Party(subscriber.id(), subscriber.reference(), subscriber.displayName(),
                subscriber.email(), subscriber.status().name());
        var included = c.getMethods().stream().map(m -> {
            PaymentMethodView method = methods.get(m.paymentMethodId());
            return new ContractView.Method(method.code(), method.name(), method.shortLabel(), method.color(),
                    method.category(), m.perTransactionLimit(), m.monthlyLimit());
        }).toList();
        return new ContractView(c.getId(), c.getReference(), c.getStatus(), party, templateViews.get(c.getTemplateId()),
                c.getStartDate(), c.getEndDate(), c.getCommissionRate(), included, c.getPdfSha256(), c.getSentAt(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private Map<UUID, PaymentMethodView> catalogById() {
        return catalog.findAll().stream().collect(Collectors.toMap(PaymentMethodView::id, Function.identity()));
    }

    private Map<String, PaymentMethodView> catalogByCode() {
        return catalog.findAll().stream().collect(Collectors.toMap(PaymentMethodView::code, Function.identity()));
    }

    private Map<UUID, TemplateView> templateViews() {
        return templates.findAll().stream().collect(Collectors.toMap(ContractTemplate::getId, ContractTemplate::toView));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String actorLabel() {
        return AuditEvent.actorLabelOf(SecurityContextHolder.getContext().getAuthentication());
    }

    private void audit(String action, Contract contract, Map<String, Object> details) {
        Map<String, Object> all = new LinkedHashMap<>(details);
        all.put("reference", contract.getReference());
        all.put("status", contract.getStatus().name());
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(), action,
                "CONTRACT", contract.getId().toString(), Outcome.SUCCESS, all));
    }

    /** Modifications d'un brouillon (étapes 2 et 3 de l'assistant). */
    public record DraftChanges(UUID templateId, LocalDate startDate, LocalDate endDate, BigDecimal commissionRate,
            List<Method> methods) {

        public DraftChanges {
            methods = methods == null ? List.of() : List.copyOf(methods);
        }

        public record Method(String code, BigDecimal perTransactionLimit, BigDecimal monthlyLimit) {
        }
    }

    public record ContractPdf(String fileName, byte[] content) {
    }
}
