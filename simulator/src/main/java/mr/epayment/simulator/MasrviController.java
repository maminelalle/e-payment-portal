package mr.epayment.simulator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import mr.epayment.simulator.PaymentEngine.NewPayment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API imitée « Masrvi » : volontairement différente de Bankily (champs en français, montant en texte,
 * états EN_COURS / SUCCES / ECHEC, codes 00 / 51 / 05 / 91) pour montrer le rôle des adaptateurs du portail.
 */
@RestController
@RequestMapping("/masrvi/v2/transactions")
class MasrviController {

    private final PaymentEngine engine;

    MasrviController(PaymentEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Simulator-Scenario", required = false) String scenario) {
        BigDecimal amount = new BigDecimal(request.montant());
        SimPayment payment = engine.create(new NewPayment(ProviderCode.MASRVI, "MSV-", request.reference(), amount,
                request.devise(), request.telephoneClient(), request.motif(), null, null,
                Scenario.of(amount, scenario)), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(payment));
    }

    @GetMapping("/{idTransaction}")
    ResponseEntity<Map<String, Object>> get(@PathVariable String idTransaction) {
        return engine.find(ProviderCode.MASRVI, idTransaction)
                .map(p -> ResponseEntity.ok(view(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    static String etat(SimPayment p) {
        return switch (p.state()) {
            case SUCCEEDED -> "SUCCES";
            case FAILED, EXPIRED -> "ECHEC";
            default -> "EN_COURS";
        };
    }

    static String code(SimPayment p) {
        if (p.state() != SimPayment.State.FAILED) {
            return "00";
        }
        return switch (String.valueOf(p.failureCode())) {
            case "INSUFFICIENT_FUNDS" -> "51";
            case "TIMEOUT" -> "91";
            default -> "05";
        };
    }

    private static Map<String, Object> view(SimPayment p) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("idTransaction", p.id());
        view.put("reference", p.merchantReference());
        view.put("etat", etat(p));
        view.put("code", code(p));
        view.put("montant", p.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        view.put("devise", p.currency());
        return view;
    }

    record Request(
            @NotBlank String reference,
            @NotBlank @Pattern(regexp = "\\d+(\\.\\d{1,2})?") String montant,
            @NotBlank String devise,
            @NotBlank @Pattern(regexp = "\\d{8,15}") String telephoneClient,
            String motif) {
    }
}
