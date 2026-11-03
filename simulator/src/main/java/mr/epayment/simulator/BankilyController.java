package mr.epayment.simulator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
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

/** API imitée « Bankily » : REST anglais, statuts PENDING / SUCCESS / FAILED. */
@RestController
@RequestMapping("/bankily/api/v1/payments")
class BankilyController {

    private final PaymentEngine engine;

    BankilyController(PaymentEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Simulator-Scenario", required = false) String scenario) {
        SimPayment payment = engine.create(new NewPayment(ProviderCode.BANKILY, "BKL-", request.merchantReference(),
                request.amount(), request.currency(), request.payerPhone(), request.description(), null, null,
                Scenario.of(request.amount(), scenario)), idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(payment));
    }

    @GetMapping("/{paymentId}")
    ResponseEntity<Map<String, Object>> get(@PathVariable String paymentId) {
        return engine.find(ProviderCode.BANKILY, paymentId)
                .map(p -> ResponseEntity.ok(view(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    static String status(SimPayment p) {
        return switch (p.state()) {
            case SUCCEEDED -> "SUCCESS";
            case FAILED, EXPIRED -> "FAILED";
            default -> "PENDING";
        };
    }

    private static Map<String, Object> view(SimPayment p) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("paymentId", p.id());
        view.put("merchantReference", p.merchantReference());
        view.put("status", status(p));
        view.put("amount", p.amount());
        view.put("currency", p.currency());
        view.put("failureCode", p.failureCode());
        view.put("createdAt", p.createdAt().toString());
        return view;
    }

    record Request(
            @NotBlank String merchantReference,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp = "\\+?\\d{8,15}") String payerPhone,
            String description) {
    }
}
