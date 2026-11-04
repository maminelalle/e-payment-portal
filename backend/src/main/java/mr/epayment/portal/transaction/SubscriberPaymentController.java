package mr.epayment.portal.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import mr.epayment.portal.shared.FieldValidationException;
import mr.epayment.portal.shared.PageResponse;
import mr.epayment.portal.transaction.PaymentService.Filter;
import mr.epayment.portal.transaction.PaymentService.PayCommand;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Paiements depuis l'espace abonné (maquette B3) et historique. */
@RestController
@RequestMapping("/api/abonne/paiements")
class SubscriberPaymentController {

    private final PaymentService payments;

    SubscriberPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** {@code Idempotency-Key} obligatoire : renvoyer la même demande ne débite jamais deux fois. */
    @PostMapping
    TransactionView pay(Authentication authentication, @Valid @RequestBody PaymentForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9-]{8,100}")) {
            throw new FieldValidationException(Map.of("idempotencyKey",
                    "En-tête Idempotency-Key obligatoire (8 à 100 caractères)."));
        }
        return payments.pay(authentication, new PayCommand(form.method(), form.amount(),
                normalizePhone(form.payerPhone()), blankToNull(form.description()), idempotencyKey));
    }

    @GetMapping
    PageResponse<TransactionView> history(Authentication authentication,
            @RequestParam(required = false) TransactionStatus statut,
            @RequestParam(required = false) String moyen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return payments.history(authentication, new Filter(null, statut, moyen, du, au, q), page, size);
    }

    @GetMapping("/totaux")
    FilteredTotals totals(Authentication authentication, @RequestParam(required = false) TransactionStatus statut,
            @RequestParam(required = false) String moyen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String q) {
        return payments.historyTotals(authentication, new Filter(null, statut, moyen, du, au, q));
    }

    /** Historique en CSV (Excel), mêmes filtres que la liste. */
    @GetMapping("/export.csv")
    ResponseEntity<byte[]> export(Authentication authentication, @RequestParam(required = false) TransactionStatus statut,
            @RequestParam(required = false) String moyen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String q) {
        return Receipts.csv("mes-transactions.csv",
                payments.exportHistoryCsv(authentication, new Filter(null, statut, moyen, du, au, q)));
    }

    /** Reçu PDF (paiement réussi uniquement). */
    @GetMapping("/{reference}/recu")
    ResponseEntity<byte[]> receipt(Authentication authentication, @PathVariable String reference) {
        return Receipts.pdf(payments.receipt(authentication, reference));
    }

    @GetMapping("/{reference}")
    TransactionView get(Authentication authentication, @PathVariable String reference,
            @RequestParam(defaultValue = "false") boolean actualiser) {
        return payments.get(authentication, reference, actualiser);
    }

    @PostMapping("/{reference}/annuler")
    TransactionView cancel(Authentication authentication, @PathVariable String reference) {
        return payments.cancel(authentication, reference);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
