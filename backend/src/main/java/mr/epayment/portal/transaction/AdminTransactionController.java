package mr.epayment.portal.transaction;

import java.time.LocalDate;
import java.util.UUID;
import mr.epayment.portal.shared.PageResponse;
import mr.epayment.portal.transaction.PaymentService.Filter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transactions de tous les abonnés, pour l'administrateur (lecture seule). */
@RestController
@RequestMapping("/api/admin/transactions")
class AdminTransactionController {

    private final PaymentService payments;

    AdminTransactionController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    PageResponse<TransactionView> search(
            @RequestParam(required = false) UUID abonne,
            @RequestParam(required = false) TransactionStatus statut,
            @RequestParam(required = false) String moyen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return payments.search(new Filter(abonne, statut, moyen, du, au, q), page, size);
    }

    /** Nombre et montant encaissé sur les filtres actifs (titre de page « 10 transactions · 161 500,00 MRU encaissés »). */
    @GetMapping("/totaux")
    FilteredTotals totals(@RequestParam(required = false) UUID abonne,
            @RequestParam(required = false) TransactionStatus statut, @RequestParam(required = false) String moyen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String q) {
        return payments.totals(new Filter(abonne, statut, moyen, du, au, q));
    }

    @GetMapping("/export.csv")
    ResponseEntity<byte[]> export(@RequestParam(required = false) UUID abonne,
            @RequestParam(required = false) TransactionStatus statut, @RequestParam(required = false) String moyen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String q) {
        return Receipts.csv("transactions.csv", payments.exportCsv(new Filter(abonne, statut, moyen, du, au, q), true));
    }

    @GetMapping("/{reference}")
    TransactionView get(@PathVariable String reference) {
        return payments.adminGet(reference);
    }

    @GetMapping("/{reference}/recu")
    ResponseEntity<byte[]> receipt(@PathVariable String reference) {
        return Receipts.pdf(payments.adminReceipt(reference));
    }
}
