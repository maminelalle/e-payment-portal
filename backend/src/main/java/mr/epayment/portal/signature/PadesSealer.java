package mr.epayment.portal.signature;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignerTextHorizontalAlignment;
import eu.europa.esig.dss.enumerations.TextWrapping;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxNativeObjectFactory;
import eu.europa.esig.dss.pdf.pdfbox.visible.PdfBoxNativeFont;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Date;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Scelle un PDF au format PAdES (niveau BASELINE-B) avec le certificat du portail (EU DSS).
 * Le sceau atteste l'intégrité du document et le consentement recueilli (code à usage unique) ;
 * les détails du signataire figurent dans la raison de la signature et dans la table signature.
 * Un cachet visible reprend ces informations dans le cadre de signature du contrat.
 */
@Component
class PadesSealer {

    private final SealingKey key;
    private final PAdESService service = new PAdESService(new CommonCertificateVerifier());

    PadesSealer(SealingKey key) {
        this.key = key;
        // Cachet dessiné en texte PDF natif (net à tout zoom, sélectionnable) plutôt qu'en image.
        service.setPdfObjFactory(new PdfBoxNativeObjectFactory());
    }

    byte[] seal(byte[] pdf, String fileName, String reason, String signerContact, Date signingTime, String stampText) {
        var parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(new CertificateToken(key.certificate()));
        parameters.setReason(reason);
        parameters.setLocation("Nouakchott, Mauritanie");
        parameters.setContactInfo(signerContact);
        parameters.bLevel().setSigningDate(signingTime);
        parameters.setImageParameters(stamp(SignatureStampPosition.locate(pdf), stampText));

        DSSDocument document = new InMemoryDocument(pdf, fileName);
        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue value = new SignatureValue(SignatureAlgorithm.RSA_SHA256, sign(dataToSign.getBytes()));
        DSSDocument sealed = service.signDocument(document, parameters, value);
        try (var out = new ByteArrayOutputStream()) {
            sealed.writeTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du PDF scellé impossible", e);
        }
    }

    private static SignatureImageParameters stamp(SignatureStampPosition position, String text) {
        var field = new SignatureFieldParameters();
        field.setPage(position.page());
        field.setOriginX(position.x());
        field.setOriginY(position.y());
        field.setWidth(position.width());
        field.setHeight(position.height());

        var font = new PdfBoxNativeFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        font.setSize(8);
        var textParameters = new SignatureImageTextParameters();
        textParameters.setText(winAnsi(text));
        textParameters.setFont(font);
        textParameters.setTextWrapping(TextWrapping.FONT_BASED);
        textParameters.setSignerTextHorizontalAlignment(SignerTextHorizontalAlignment.LEFT);
        textParameters.setPadding(6);
        textParameters.setTextColor(new Color(0x1e, 0x3a, 0x8a));
        textParameters.setBackgroundColor(new Color(0xef, 0xf6, 0xff));

        var image = new SignatureImageParameters();
        image.setFieldParameters(field);
        image.setTextParameters(textParameters);
        image.setBackgroundColor(new Color(0xef, 0xf6, 0xff));
        return image;
    }

    /** La police standard Helvetica ne couvre que WinAnsi : tout autre caractère (arabe, etc.) devient « ? ». */
    static String winAnsi(String text) {
        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        var out = new StringBuilder(text.length());
        text.codePoints().mapToObj(Character::toString).forEach(c -> {
            try {
                font.encode(c);
                out.append(c);
            } catch (IllegalArgumentException | IOException e) {
                out.append(c.equals("\n") ? c : "?");
            }
        });
        return out.toString();
    }

    private byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key.privateKey());
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Scellement impossible", e);
        }
    }
}
