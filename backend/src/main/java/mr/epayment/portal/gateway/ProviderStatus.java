package mr.epayment.portal.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disponibilité des fournisseurs (Bankily, Masrvi, passerelle Visa) : une requête courte vers l'adresse de chaque
 * API. Toute réponse HTTP sous 500 compte comme « en ligne » (l'API répond, même un 404 sur la racine) ;
 * délai dépassé, connexion refusée ou erreur 5xx = « en panne ». Résultat gardé 30 secondes pour ne pas
 * solliciter les fournisseurs à chaque affichage. En mode {@code mock}, les fournisseurs sont simulés en mémoire.
 */
@Service
public class ProviderStatus {

    private static final Duration CACHE = Duration.ofSeconds(30);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final ObjectProvider<ProvidersProperties> properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final AtomicReference<Snapshot> last = new AtomicReference<>();

    ProviderStatus(ObjectProvider<ProvidersProperties> properties) {
        this.properties = properties;
    }

    public Snapshot current(boolean refresh) {
        Snapshot cached = last.get();
        if (!refresh && cached != null && cached.checkedAt().plus(CACHE).isAfter(Instant.now())) {
            return cached;
        }
        Snapshot fresh = probeAll();
        last.set(fresh);
        return fresh;
    }

    private Snapshot probeAll() {
        ProvidersProperties settings = properties.getIfAvailable();
        if (settings == null) {
            return new Snapshot(Instant.now(), List.of(
                    simulated("BANKILY", "Bankily"), simulated("MASRVI", "Masrvi"),
                    simulated("VISA_GATEWAY", "Visa / Mastercard")));
        }
        List<Thread> threads = new ArrayList<>();
        Provider[] results = new Provider[3];
        Object[][] targets = {
                {"BANKILY", "Bankily", settings.bankily()},
                {"MASRVI", "Masrvi", settings.masrvi()},
                {"VISA_GATEWAY", "Visa / Mastercard", settings.visa()}};
        for (int i = 0; i < targets.length; i++) {
            int index = i;
            threads.add(Thread.ofVirtual().start(() -> results[index] = probe((String) targets[index][0],
                    (String) targets[index][1], (ProvidersProperties.Provider) targets[index][2])));
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return new Snapshot(Instant.now(), List.of(results));
    }

    private Provider probe(String code, String name, ProvidersProperties.Provider provider) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(provider.baseUrl()))
                    .timeout(TIMEOUT).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Provider(code, name, status < 500 ? State.UP : State.DOWN, ms,
                    status < 500 ? null : "Erreur %d du fournisseur".formatted(status));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Provider(code, name, State.DOWN, null, "Vérification interrompue");
        } catch (Exception e) {
            return new Provider(code, name, State.DOWN, null, "Le fournisseur ne répond pas");
        }
    }

    private static Provider simulated(String code, String name) {
        return new Provider(code, name, State.SIMULATED, null, null);
    }

    public enum State { UP, DOWN, SIMULATED }

    /** @param latencyMs temps de réponse, null si le fournisseur n'a pas répondu */
    public record Provider(String code, String name, State state, Long latencyMs, String message) {
    }

    public record Snapshot(Instant checkedAt, List<Provider> providers) {
    }

    @RestController
    @RequestMapping("/api/admin/fournisseurs/etat")
    static class AdminController {

        private final ProviderStatus status;

        AdminController(ProviderStatus status) {
            this.status = status;
        }

        @GetMapping
        Snapshot get(@RequestParam(defaultValue = "false") boolean actualiser) {
            return status.current(actualiser);
        }
    }

    /** Page publique de statut : uniquement l'état, sans adresse ni temps de réponse. */
    @RestController
    @RequestMapping("/api/public/statut")
    static class PublicController {

        private final ProviderStatus status;

        PublicController(ProviderStatus status) {
            this.status = status;
        }

        @GetMapping
        PublicStatus get() {
            Snapshot s = status.current(false);
            return new PublicStatus(s.checkedAt(), s.providers().stream()
                    .map(p -> new PublicProvider(p.name(), p.state())).toList());
        }

        record PublicStatus(Instant checkedAt, List<PublicProvider> providers) {
        }

        record PublicProvider(String name, State state) {
        }
    }
}
