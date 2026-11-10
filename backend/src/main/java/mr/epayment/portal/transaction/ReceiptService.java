package mr.epayment.portal.transaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.contract.ContractService;
import mr.epayment.portal.document.DocumentStorage;
import mr.epayment.portal.document.PdfRenderer;
import mr.epayment.portal.shared.Amounts;
import mr.epayment.portal.shared.BusinessRuleException;
import mr.epayment.portal.subscriber.SubscriberService;
import mr.epayment.portal.subscriber.SubscriberView;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reçus PDF des paiements réussis. Généré au premier téléchargement puis conservé dans le stockage :
 * le même reçu est servi ensuite (le document ne change plus).
 */
@Service
class ReceiptService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Nouakchott");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);

    private final TransactionRepository transactions;
    private final SubscriberService subscribers;
    private final ContractService contracts;
    private final PdfRenderer pdfRenderer;
    private final DocumentStorage storage;
    private final ReceiptVerification verification;
    private final String template;

    ReceiptService(TransactionRepository transactions, SubscriberService subscribers, ContractService contracts,
            PdfRenderer pdfRenderer, DocumentStorage storage, ReceiptVerification verification) {
        this.verification = verification;
        this.transactions = transactions;
        this.subscribers = subscribers;
        this.contracts = contracts;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        try {
            this.template = new ClassPathResource("documents/recu.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Modèle de reçu introuvable", e);
        }
    }

    @Transactional
    byte[] receipt(PaymentTransaction tx, PaymentMethodView method) {
        if (tx.getStatus() != TransactionStatus.SUCCEEDED) {
            throw new BusinessRuleException("Le reçu n'existe que pour un paiement réussi.");
        }
        if (tx.getReceiptObjectKey() != null) {
            return storage.get(tx.getReceiptObjectKey());
        }
        byte[] pdf = pdfRenderer.renderPdf(template, variables(tx, method));
        String key = "recus/%s.pdf".formatted(tx.getReference());
        storage.put(key, pdf, "application/pdf");
        PaymentTransaction managed = transactions.findById(tx.getId()).orElseThrow();
        managed.attachReceipt(key);
        return pdf;
    }

    private Map<String, Object> variables(PaymentTransaction tx, PaymentMethodView method) {
        SubscriberView subscriber = subscribers.get(tx.getSubscriberId());
        boolean card = "CARD".equals(method.category());
        Map<String, Object> vars = new HashMap<>();
        vars.put("reference", tx.getReference());
        vars.put("amount", Amounts.format(tx.getAmount()) + " " + tx.getCurrency());
        vars.put("date", DATE_TIME.format(tx.getCompletedAt() == null ? tx.getCreatedAt() : tx.getCompletedAt()));
        vars.put("payer", subscriber.displayName() + " (" + subscriber.reference() + ")");
        vars.put("method", method.name());
        vars.put("instrumentLabel", card ? "Carte" : "Wallet");
        vars.put("instrument", card ? tx.getCardMaskedPan() : maskPhone(tx.getPayerPhone()));
        vars.put("description", tx.getDescription());
        vars.put("providerReference", tx.getProviderReference());
        vars.put("contract", contracts.get(tx.getContractId()).reference());
        // Commission prévue au contrat (article 4), calculée sur ce paiement réussi.
        vars.put("commission", tx.getCommissionAmount() == null ? null
                : Amounts.format(tx.getCommissionAmount()) + " " + tx.getCurrency() + " ("
                        + Amounts.format(tx.getCommissionRate()) + " %)");
        vars.put("qr", verification.qrDataUri(tx));
        vars.put("verificationCode", verification.code(tx.getReference(), tx.getAmount()));
        return Map.of("tx", vars, "issuedAt", DATE.format(Instant.now()));
    }

    /** +22222000001 → +222 ******01 */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return phone;
        }
        return phone.substring(0, 4) + " ******" + phone.substring(phone.length() - 2);
    }

}
