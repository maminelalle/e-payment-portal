package mr.epayment.portal.entitlement;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Fiche abonné (conception §3.2) : les moyens de paiement que son contrat en vigueur lui ouvre. */
@RestController
class SubscriberEntitlementsAdminController {

    private final EntitlementEngine engine;

    SubscriberEntitlementsAdminController(EntitlementEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/api/admin/abonnes/{id}/moyens")
    List<EntitledMethod> methods(@PathVariable UUID id) {
        return engine.methodsOf(id);
    }
}
