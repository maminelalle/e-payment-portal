package mr.epayment.portal.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import mr.epayment.portal.document.PdfRenderer;
import mr.epayment.portal.shared.BusinessRuleException;
import mr.epayment.portal.shared.NotFoundException;
import mr.epayment.portal.subscriber.SubscriberStatus;
import mr.epayment.portal.subscriber.SubscriberType;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modèles de contrat côté admin (conception §3.4) : versions, contrats rattachés, aperçu, choix des versions
 * proposées dans l'assistant. Le contenu HTML n'est jamais saisi dans le portail : une nouvelle version arrive
 * par migration (Thymeleaf exécute du code, D14) ; l'admin décide quelles versions sont proposées.
 */
@Service
public class ContractTemplateService {

    private final ContractTemplateRepository templates;
    private final ContractRepository contracts;
    private final PaymentCatalog catalog;
    private final PdfRenderer pdfRenderer;
    private final AuditService auditService;

    ContractTemplateService(ContractTemplateRepository templates, ContractRepository contracts, PaymentCatalog catalog,
            PdfRenderer pdfRenderer, AuditService auditService) {
        this.templates = templates;
        this.contracts = contracts;
        this.catalog = catalog;
        this.pdfRenderer = pdfRenderer;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> all() {
        return templates.findAllOrdered().stream().map(this::summary).toList();
    }

    /** Propose (ACTIVE) ou retire (ARCHIVED) une version ; au moins une version reste proposée. */
    @Transactional
    public TemplateSummary changeStatus(UUID id, boolean active) {
        ContractTemplate template = find(id);
        if (!active && template.isActive() && templates.findActive().size() <= 1) {
            throw new BusinessRuleException("C'est le seul modèle proposé : réactivez-en un autre avant de l'archiver.");
        }
        template.changeStatus(active);
        auditService.record(AuditEvent.of(SecurityContextHolder.getContext().getAuthentication(),
                active ? "CONTRACT_TEMPLATE_ACTIVATED" : "CONTRACT_TEMPLATE_ARCHIVED", "CONTRACT_TEMPLATE",
                template.getCode() + " v" + template.getVersion(), Outcome.SUCCESS, Map.of("name", template.getName())));
        return summary(template);
    }

    /** Aperçu PDF du modèle avec un abonné et des moyens d'exemple (filigrane « BROUILLON »). */
    @Transactional(readOnly = true)
    public byte[] preview(UUID id) {
        ContractTemplate template = find(id);
        Map<UUID, PaymentMethodView> methods = catalog.findAll().stream()
                .collect(Collectors.toMap(PaymentMethodView::id, Function.identity()));
        Contract sample = new Contract("CTR-EXEMPLE", UUID.randomUUID(), template.getId(), "aperçu");
        LocalDate today = LocalDate.now(ContractDocument.ZONE);
        sample.updateDraft(template.getId(), today, today.plusYears(1), new BigDecimal("1.50"),
                methods.values().stream()
                        .filter(m -> m.status() == PaymentMethodStatus.ACTIVE)
                        .limit(3)
                        .map(m -> new IncludedMethod(m.id(), new BigDecimal("50000"), new BigDecimal("500000")))
                        .toList());
        SubscriberView subscriber = new SubscriberView(UUID.randomUUID(), "ABN-0000", SubscriberType.MORALE,
                "Société Exemple SARL", null, "12345678", "+22222000000", "contact@exemple.mr",
                "Tevragh Zeina, Nouakchott", SubscriberStatus.ACTIVE, Instant.now(), Instant.now(), null);
        return pdfRenderer.renderPdf(template.getContentHtml(),
                ContractDocument.variables(sample, subscriber, methods, true, null));
    }

    private TemplateSummary summary(ContractTemplate t) {
        return new TemplateSummary(t.getId(), t.getCode(), t.getName(), t.getVersion(), t.getStatus(),
                t.getCreatedAt(), contracts.countByTemplateId(t.getId()));
    }

    private ContractTemplate find(UUID id) {
        return templates.findById(id).orElseThrow(() -> new NotFoundException("Modèle de contrat introuvable."));
    }

    /** @param contractCount contrats établis avec cette version (brouillons compris) */
    public record TemplateSummary(UUID id, String code, String name, int version, String status, Instant createdAt,
            long contractCount) {
    }
}
