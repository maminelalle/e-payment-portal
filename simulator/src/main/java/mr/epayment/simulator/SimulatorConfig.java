package mr.epayment.simulator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Chaque API exige sa clé : {@code Authorization: Bearer <apiKey>}. La page de paiement carte
 * ({@code /visa-gateway/pay/**}) est publique : c'est le navigateur de l'abonné qui l'ouvre.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
class SimulatorConfig implements WebMvcConfigurer {

    private static final Map<String, ProviderCode> API_PREFIXES = Map.of(
            "/bankily/api/", ProviderCode.BANKILY,
            "/masrvi/v2/", ProviderCode.MASRVI,
            "/visa-gateway/api/", ProviderCode.VISA);

    private final SimulatorProperties properties;

    SimulatorConfig(SimulatorProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                ProviderCode provider = API_PREFIXES.entrySet().stream()
                        .filter(e -> request.getRequestURI().startsWith(e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
                if (provider == null) {
                    return true;
                }
                String expected = "Bearer " + properties.provider(provider).apiKey();
                String given = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        given.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"invalid_api_key\"}");
                return false;
            }
        }).addPathPatterns("/bankily/api/**", "/masrvi/v2/**", "/visa-gateway/api/**");
    }
}
