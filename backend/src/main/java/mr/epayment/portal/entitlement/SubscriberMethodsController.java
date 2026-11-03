package mr.epayment.portal.entitlement;

import java.util.List;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** « Mes moyens de paiement » : seuls les moyens ouverts par le contrat en vigueur de l'abonné connecté. */
@RestController
@RequestMapping("/api/abonne/moyens")
class SubscriberMethodsController {

    private final EntitlementEngine engine;
    private final CurrentSubscriber currentSubscriber;

    SubscriberMethodsController(EntitlementEngine engine, CurrentSubscriber currentSubscriber) {
        this.engine = engine;
        this.currentSubscriber = currentSubscriber;
    }

    @GetMapping
    List<EntitledMethod> mine(Authentication authentication) {
        return engine.methodsOf(currentSubscriber.require(authentication).id());
    }
}
