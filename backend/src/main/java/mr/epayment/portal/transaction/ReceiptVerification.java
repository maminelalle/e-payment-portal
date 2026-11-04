package mr.epayment.portal.transaction;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import mr.epayment.portal.catalog.PaymentCatalog;
import mr.epayment.portal.catalog.PaymentMethodView;
import mr.epayment.portal.subscriber.SubscriberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification d'un reçu par QR code. Le QR mène à {@code <portail>/verification/recu/TRX-…?c=<code>} ;
 * le code est un HMAC de la référence et du montant : sans lui, la page publique ne révèle rien
 * (impossible d'énumérer les références), avec lui elle confirme que le paiement existe et a réussi.
 */
@Service
class ReceiptVerification {

    private final byte[] secret;
    private final String portalUrl;
    private final TransactionRepository transactions;
    private final SubscriberService subscribers;
    private final PaymentCatalog catalog;

    ReceiptVerification(@Value("${epayment.receipts.verification-secret}") String secret,
            @Value("${epayment.portal-url}") String portalUrl, TransactionRepository transactions,
            SubscriberService subscribers, PaymentCatalog catalog) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.portalUrl = portalUrl.replaceAll("/+$", "");
        this.transactions = transactions;
        this.subscribers = subscribers;
        this.catalog = catalog;
    }

    String code(String reference, BigDecimal amount) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((reference + "|" + amount.stripTrailingZeros().toPlainString())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    String url(PaymentTransaction tx) {
        return portalUrl + "/verification/recu/" + tx.getReference() + "?c=" + code(tx.getReference(), tx.getAmount());
    }

    /** QR code de l'adresse de vérification, en PNG {@code data:} pour le modèle HTML du reçu. */
    String qrDataUri(PaymentTransaction tx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url(tx), BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            int scale = 6;
            BufferedImage image = new BufferedImage(matrix.getWidth() * scale, matrix.getHeight() * scale,
                    BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x / scale, y / scale) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    Result verify(String reference, String code) {
        var found = transactions.findByReference(reference);
        if (found.isEmpty() || code == null || !MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                code(reference, found.get().getAmount()).getBytes(StandardCharsets.UTF_8))) {
            return Result.invalid(reference);
        }
        PaymentTransaction tx = found.get();
        if (tx.getStatus() != TransactionStatus.SUCCEEDED) {
            return Result.invalid(reference);
        }
        PaymentMethodView method = catalog.findAll().stream()
                .filter(m -> m.id().equals(tx.getPaymentMethodId())).findFirst().orElseThrow();
        return new Result(true, tx.getReference(), tx.getAmount(), tx.getCurrency(),
                tx.getCompletedAt() == null ? tx.getCreatedAt() : tx.getCompletedAt(), method.name(),
                subscribers.get(tx.getSubscriberId()).displayName());
    }

    /** @param valid faux : référence inconnue, code faux ou paiement non réussi (sans plus de détail) */
    record Result(boolean valid, String reference, BigDecimal amount, String currency, Instant paidAt,
            String methodName, String payee) {

        static Result invalid(String reference) {
            return new Result(false, reference, null, null, null, null, null);
        }
    }

    @RestController
    @RequestMapping("/api/public/recus")
    static class Controller {

        private final ReceiptVerification verification;

        Controller(ReceiptVerification verification) {
            this.verification = verification;
        }

        @GetMapping("/{reference}")
        Result verify(@PathVariable String reference, @RequestParam(name = "c", required = false) String code) {
            return verification.verify(reference, code);
        }
    }
}
