package mr.epayment.portal.entitlement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import mr.epayment.portal.contract.ContractView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cycle de vie d'un contrat signé, piloté par l'administrateur : chaque action ouvre ou ferme ses droits. */
@RestController
@RequestMapping("/api/admin/contrats/{id}")
class ContractLifecycleController {

    private final EntitlementEngine engine;

    ContractLifecycleController(EntitlementEngine engine) {
        this.engine = engine;
    }

    /** Relance manuelle de l'activation (normalement automatique après la signature). */
    @PostMapping("/activer")
    ContractView activate(@PathVariable UUID id) {
        return engine.activateContract(id);
    }

    @PostMapping("/suspendre")
    ContractView suspend(@PathVariable UUID id, @Valid @RequestBody(required = false) Reason reason) {
        return engine.suspendContract(id, reason == null ? null : reason.motif());
    }

    @PostMapping("/reprendre")
    ContractView resume(@PathVariable UUID id) {
        return engine.resumeContract(id);
    }

    @PostMapping("/resilier")
    ContractView terminate(@PathVariable UUID id, @Valid @RequestBody(required = false) Reason reason) {
        return engine.terminateContract(id, reason == null ? null : reason.motif());
    }

    @GetMapping("/droits")
    List<EntitledMethod> entitlements(@PathVariable UUID id) {
        return engine.entitlementsOfContract(id);
    }

    record Reason(@Size(max = 300, message = "300 caractères au maximum.") String motif) {
    }
}
