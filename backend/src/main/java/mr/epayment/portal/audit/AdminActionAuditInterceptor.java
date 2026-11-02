package mr.epayment.portal.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import mr.epayment.portal.audit.AuditEvent.Outcome;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Journalise toute requête modifiante de l'API d'administration, qu'elle réussisse ou non.
 * Les modules ajoutent en plus leurs propres événements métier (SUBSCRIBER_CREATED…).
 */
@Configuration
class AdminActionAuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String ACTION = "ADMIN_REQUEST";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    AdminActionAuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return;
        }
        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
        Outcome outcome = status < 400 ? Outcome.SUCCESS : Outcome.FAILURE;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        auditService.record(AuditEvent.of(authentication, ACTION, "HTTP", request.getRequestURI(), outcome,
                Map.of("method", request.getMethod(), "status", status)));
    }
}
