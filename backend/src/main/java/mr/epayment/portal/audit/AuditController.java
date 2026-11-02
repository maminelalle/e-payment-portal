package mr.epayment.portal.audit;

import mr.epayment.portal.shared.PageResponse;
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

    @GetMapping
    PageResponse<AuditEntry> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String acteur,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditService.search(action, acteur, page, size);
    }
}
