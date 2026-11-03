package mr.epayment.portal.subscriber;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import mr.epayment.portal.shared.PageResponse;
import mr.epayment.portal.subscriber.SubscriberService.CreatedSubscriber;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion des abonnés par l'administrateur. */
@RestController
@RequestMapping("/api/admin/abonnes")
class SubscriberAdminController {

    private final SubscriberService service;

    SubscriberAdminController(SubscriberService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<SubscriberView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SubscriberStatus statut,
            @RequestParam(required = false) SubscriberType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(q, statut, type, page, size);
    }

    /** Compteurs par statut, pour l'en-tête de la liste et le tableau de bord. */
    @GetMapping("/resume")
    Map<SubscriberStatus, Long> summary() {
        return service.countByStatus();
    }

    @GetMapping("/{id}")
    SubscriberView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedSubscriber create(@Valid @RequestBody NewSubscriber request) {
        return service.create(request.type(), request.email(), request.details());
    }

    @PutMapping("/{id}")
    SubscriberView update(@PathVariable UUID id, @Valid @RequestBody SubscriberDetails details) {
        return service.update(id, details);
    }

    @PostMapping("/{id}/activer")
    SubscriberView activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/suspendre")
    SubscriberView suspend(@PathVariable UUID id, @Valid @RequestBody(required = false) Reason reason) {
        return service.suspend(id, reason == null ? null : reason.motif());
    }

    @PostMapping("/{id}/desactiver")
    SubscriberView disable(@PathVariable UUID id, @Valid @RequestBody(required = false) Reason reason) {
        return service.disable(id, reason == null ? null : reason.motif());
    }

    @PostMapping("/{id}/invitation")
    Map<String, Boolean> resendInvitation(@PathVariable UUID id) {
        return Map.of("invitationSent", service.resendInvitation(id));
    }

    record NewSubscriber(
            @NotNull(message = "Le type d'abonné est obligatoire.") SubscriberType type,
            @NotBlank(message = "L'email est obligatoire.")
            @Email(message = "Adresse email invalide.")
            @Size(max = 254, message = "254 caractères au maximum.")
            String email,
            @NotNull @Valid SubscriberDetails details) {
    }

    record Reason(@Size(max = 300, message = "300 caractères au maximum.") String motif) {
    }
}
