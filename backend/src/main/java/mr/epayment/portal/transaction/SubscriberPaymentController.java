package mr.epayment.portal.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import mr.epayment.portal.transaction.PaymentService.PaymentResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paiement depuis l'espace abonné (maquette B3). */
@RestController
@RequestMapping("/api/abonne/paiements")
class SubscriberPaymentController {

    private final PaymentService payments;

    SubscriberPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    PaymentResult pay(Authentication authentication, @Valid @RequestBody PaymentForm form) {
        return payments.pay(authentication, form.method(), form.amount(), normalizePhone(form.payerPhone()),
                form.description());
    }

    @ExceptionHandler(PaymentRefusedException.class)
    ProblemDetail refused(PaymentRefusedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                e.isLimit() ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.FORBIDDEN, e.getMessage());
        problem.setProperty("refusal", e.refusal().name());
        return problem;
    }

    private static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[\\s.\\-()]", "");
        if (digits.matches("\\d{8}")) {
            return "+222" + digits;
        }
        return digits.startsWith("00222") ? "+" + digits.substring(2) : digits;
    }

    record PaymentForm(
            @NotBlank(message = "Choisissez un moyen de paiement.") String method,
            @NotNull(message = "Indiquez le montant.")
            @DecimalMin(value = "1.00", message = "Le montant minimum est de 1 MRU.")
            @Digits(integer = 13, fraction = 2, message = "Montant invalide.")
            BigDecimal amount,
            @Pattern(regexp = "[+\\d\\s.\\-()]{8,20}", message = "Numéro de téléphone invalide.") String payerPhone,
            @Size(max = 140, message = "140 caractères au maximum.") String description) {
    }
}
