package mr.epayment.portal.transaction;

import mr.epayment.portal.transaction.PaymentService.Receipt;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Réponse HTTP d'un reçu PDF (téléchargement). */
final class Receipts {

    private Receipts() {
    }

    static ResponseEntity<byte[]> pdf(Receipt receipt) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(receipt.content());
    }
}
