package mr.epayment.portal.contract;

import mr.epayment.portal.contract.ContractService.ContractPdf;
import mr.epayment.portal.subscriber.CurrentSubscriber;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** « Mon contrat » : l'abonné ne voit que son propre contrat, retrouvé à partir de son jeton. */
@RestController
@RequestMapping("/api/abonne/contrat")
class SubscriberContractController {

    private final ContractService contracts;
    private final CurrentSubscriber currentSubscriber;

    SubscriberContractController(ContractService contracts, CurrentSubscriber currentSubscriber) {
        this.contracts = contracts;
        this.currentSubscriber = currentSubscriber;
    }

    /** 204 si aucun contrat ne lui a encore été envoyé. */
    @GetMapping
    ResponseEntity<ContractView> current(Authentication authentication) {
        var subscriber = currentSubscriber.require(authentication);
        return contracts.currentForSubscriber(subscriber.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/pdf")
    ResponseEntity<byte[]> pdf(Authentication authentication) {
        ContractPdf pdf = contracts.currentPdfForSubscriber(currentSubscriber.require(authentication).id());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }
}
