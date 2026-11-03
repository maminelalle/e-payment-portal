package mr.epayment.portal.audit;

import java.time.LocalDate;
import java.util.List;
import mr.epayment.portal.audit.AuditService.Filter;
import mr.epayment.portal.shared.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultation du journal d'audit (réservée aux administrateurs, voir SecurityConfig). */
@RestController
@RequestMapping("/api/admin/audit")
class AuditController {

    private final AuditService auditService;

    AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * @param ressource partie de l'identifiant de la ressource (TRX-000005, CTR-2026-0001…)
     * @param resultat SUCCESS ou FAILURE
     */
    @GetMapping
    PageResponse<AuditEntry> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String acteur,
            @RequestParam(required = false) String ressource,
            @RequestParam(required = false) String resultat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditService.search(new Filter(action, acteur, ressource, resultat, du, au), page, size);
    }

    @GetMapping("/actions")
    List<String> actions() {
        return auditService.actions();
    }
}
