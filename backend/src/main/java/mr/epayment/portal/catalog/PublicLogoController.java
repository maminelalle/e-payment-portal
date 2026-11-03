package mr.epayment.portal.catalog;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logos des moyens de paiement, publics : une balise {@code <img>} ne peut pas envoyer le jeton de connexion,
 * et un logo de marque n'a rien de confidentiel. L'adresse est versionnée ({@code ?v=}) : cache long sans risque.
 */
@RestController
class PublicLogoController {

    private final PaymentCatalog catalog;

    PublicLogoController(PaymentCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/public/moyens/{code}/logo")
    ResponseEntity<byte[]> logo(@PathVariable String code) {
        return catalog.logo(code)
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'")
                        .body(logo.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
