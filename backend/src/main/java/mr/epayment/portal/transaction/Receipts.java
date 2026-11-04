package mr.epayment.portal.transaction;

import java.nio.charset.StandardCharsets;
import mr.epayment.portal.transaction.PaymentService.Receipt;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Réponses HTTP de téléchargement : reçu PDF, export CSV. */
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

    static ResponseEntity<byte[]> csv(String fileName, byte[] content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }
}
