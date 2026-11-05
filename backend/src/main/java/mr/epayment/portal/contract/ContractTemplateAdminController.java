package mr.epayment.portal.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import mr.epayment.portal.contract.ContractTemplateService.TemplateSummary;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Modèles de contrat : versions, aperçu, versions proposées dans l'assistant. */
@RestController
@RequestMapping("/api/admin/modeles")
class ContractTemplateAdminController {

    private final ContractTemplateService service;

    ContractTemplateAdminController(ContractTemplateService service) {
        this.service = service;
    }

    @GetMapping
    List<TemplateSummary> list() {
        return service.all();
    }

    @PatchMapping("/{id}/statut")
    TemplateSummary changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChange request) {
        return service.changeStatus(id, request.active());
    }

    @GetMapping("/{id}/apercu")
    ResponseEntity<byte[]> preview(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("modele-apercu.pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.preview(id));
    }

    record StatusChange(@NotNull Boolean active) {
    }
}
