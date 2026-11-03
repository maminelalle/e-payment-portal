package mr.epayment.portal.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mr.epayment.portal.contract.ContractService.ContractPdf;
import mr.epayment.portal.contract.ContractService.DraftChanges;
import mr.epayment.portal.contract.ContractTemplate.TemplateView;
import mr.epayment.portal.shared.PageResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Contrats côté administrateur (assistant de création de la maquette A4). */
@RestController
@RequestMapping("/api/admin/contrats")
class ContractAdminController {

    private final ContractService service;

    ContractAdminController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ContractView> search(
            @RequestParam(required = false) ContractStatus statut,
            @RequestParam(required = false) UUID abonne,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(statut, abonne, q, page, size);
    }

    @GetMapping("/modeles")
    List<TemplateView> templates() {
        return service.activeTemplates();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContractView create(@Valid @RequestBody NewDraft request) {
        return service.createDraft(request.subscriberId(), request.templateId());
    }

    @GetMapping("/{id}")
    ContractView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    ContractView update(@PathVariable UUID id, @Valid @RequestBody DraftRequest request) {
        return service.updateDraft(id, request.toChanges());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.deleteDraft(id);
    }

    @PostMapping("/{id}/envoyer")
    ContractView send(@PathVariable UUID id) {
        return service.sendForSignature(id);
    }

    @GetMapping("/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        ContractPdf pdf = service.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }

    record NewDraft(
            @NotNull(message = "Choisissez un abonné.") UUID subscriberId,
            @NotNull(message = "Choisissez un modèle de contrat.") UUID templateId) {
    }

    record DraftRequest(
            @NotNull(message = "Choisissez un modèle de contrat.") UUID templateId,
            LocalDate startDate,
            LocalDate endDate,
            @DecimalMin(value = "0", message = "La commission ne peut pas être négative.")
            @DecimalMax(value = "100", message = "La commission ne peut pas dépasser 100 %.")
            @Digits(integer = 3, fraction = 2, message = "Deux décimales au maximum.")
            BigDecimal commissionRate,
            @Size(max = 20) List<@Valid MethodLimits> methods) {

        DraftChanges toChanges() {
            return new DraftChanges(templateId, startDate, endDate, commissionRate, methods == null ? List.of()
                    : methods.stream()
                            .map(m -> new DraftChanges.Method(m.code(), m.perTransactionLimit(), m.monthlyLimit()))
                            .toList());
        }
    }

    record MethodLimits(
            @NotBlank String code,
            @NotNull(message = "Plafond par transaction obligatoire.")
            @Positive(message = "Le plafond doit être positif.")
            @Digits(integer = 13, fraction = 2, message = "Montant invalide.")
            BigDecimal perTransactionLimit,
            @NotNull(message = "Plafond mensuel obligatoire.")
            @Positive(message = "Le plafond doit être positif.")
            @Digits(integer = 13, fraction = 2, message = "Montant invalide.")
            BigDecimal monthlyLimit) {
    }
}
