package mr.epayment.portal.audit;

import jakarta.servlet.http.HttpServletRequest;
import mr.epayment.portal.shared.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/** Point d'entrée unique pour journaliser les actions sensibles et consulter le journal. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;

    AuditService(AuditLogRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Enregistre l'événement dans sa propre transaction : il est conservé même si l'opération
     * métier échoue ensuite. Une erreur d'écriture est tracée mais n'interrompt pas la requête.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        try {
            String details = event.details().isEmpty() ? null : jsonMapper.writeValueAsString(event.details());
            repository.save(new AuditLog(event, currentClientIp(), details));
        } catch (RuntimeException e) {
            log.error("Échec d'écriture du journal d'audit pour l'action {}", event.action(), e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> search(String action, String actor, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.of(repository.search(blankToNull(action), blankToNull(actor), pageable), AuditLog::toEntry);
    }

    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
