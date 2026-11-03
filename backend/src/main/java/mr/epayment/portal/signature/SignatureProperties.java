package mr.epayment.portal.signature;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres {@code epayment.signature.*}.
 *
 * @param otpSecret        clé HMAC des codes à usage unique (jamais stockés en clair)
 * @param otpValidity      durée de validité d'un code (5 min)
 * @param resendDelay      délai minimal avant de redemander un code
 * @param maxSends         nombre maximal de codes envoyés pour une même tentative
 * @param sealKeystore     keystore PKCS#12 du certificat de scellement ; vide = certificat de développement
 * @param sealKeystorePassword mot de passe du keystore
 */
@ConfigurationProperties("epayment.signature")
record SignatureProperties(String otpSecret, Duration otpValidity, Duration resendDelay, int maxSends,
        String sealKeystore, String sealKeystorePassword) {
}
