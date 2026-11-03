package mr.epayment.portal.signature;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Clé et certificat de scellement du portail, utilisés pour sceller (PAdES) les contrats signés.
 * <p>
 * Production : keystore PKCS#12 fourni par configuration (certificat délivré par une autorité reconnue).
 * Développement : certificat auto-signé généré en mémoire au démarrage, pour ne versionner aucune clé privée.
 * Les PDF déjà scellés restent vérifiables : le certificat est intégré à chaque signature.
 */
@Component
class SealingKey {

    private static final Logger log = LoggerFactory.getLogger(SealingKey.class);

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    SealingKey(SignatureProperties properties) throws GeneralSecurityException, IOException {
        if (StringUtils.hasText(properties.sealKeystore())) {
            char[] password = properties.sealKeystorePassword().toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(properties.sealKeystore())) {
                keyStore.load(in, password);
            }
            String alias = keyStore.aliases().nextElement();
            this.privateKey = (PrivateKey) keyStore.getKey(alias, password);
            this.certificate = (X509Certificate) keyStore.getCertificate(alias);
            log.info("Certificat de scellement chargé : {}", certificate.getSubjectX500Principal());
        } else {
            KeyPair keyPair = generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.certificate = selfSigned(keyPair);
            log.warn("Certificat de scellement de DÉVELOPPEMENT (auto-signé) : configurer "
                    + "epayment.signature.seal-keystore en production.");
        }
    }

    PrivateKey privateKey() {
        return privateKey;
    }

    X509Certificate certificate() {
        return certificate;
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, new SecureRandom());
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair keyPair) throws GeneralSecurityException {
        try {
            var subject = new X500Name("CN=Portail E-Payment - scellement (developpement), O=Portail E-Payment, C=MR");
            Instant now = Instant.now();
            var builder = new JcaX509v3CertificateBuilder(subject, new BigInteger(64, new SecureRandom()),
                    Date.from(now.minus(Duration.ofMinutes(5))), Date.from(now.plus(Duration.ofDays(730))),
                    subject, keyPair.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                    .addExtension(Extension.keyUsage, true,
                            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (OperatorCreationException | IOException e) {
            throw new GeneralSecurityException("Création du certificat de développement impossible", e);
        }
    }
}
