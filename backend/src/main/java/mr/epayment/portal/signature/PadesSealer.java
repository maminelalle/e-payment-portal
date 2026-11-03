package mr.epayment.portal.signature;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Scelle un PDF au format PAdES (niveau BASELINE-B) avec le certificat du portail (EU DSS).
 * Le sceau atteste l'intégrité du document et le consentement recueilli (code à usage unique) ;
 * les détails du signataire figurent dans la raison de la signature et dans la table signature.
 */
@Component
class PadesSealer {

    private final SealingKey key;
    private final PAdESService service = new PAdESService(new CommonCertificateVerifier());

    PadesSealer(SealingKey key) {
        this.key = key;
    }

    byte[] seal(byte[] pdf, String fileName, String reason, String signerContact, Date signingTime) {
        var parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(new CertificateToken(key.certificate()));
        parameters.setReason(reason);
        parameters.setLocation("Nouakchott, Mauritanie");
        parameters.setContactInfo(signerContact);
        parameters.bLevel().setSigningDate(signingTime);

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
