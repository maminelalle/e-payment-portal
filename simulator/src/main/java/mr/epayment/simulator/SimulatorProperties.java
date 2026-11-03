package mr.epayment.simulator;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres {@code simulator.*} (voir application.yml). */
@ConfigurationProperties("simulator")
public record SimulatorProperties(
        String publicUrl,
        Duration confirmDelay,
        Duration delayedConfirmDelay,
        List<Duration> webhookRetryDelays,
        Duration checkoutSessionValidity,
        Map<String, Provider> providers) {

    public Provider provider(ProviderCode code) {
        return providers.get(code.configKey());
    }

    public record Provider(String apiKey, String webhookSecret, String webhookUrl) {
    }
}
