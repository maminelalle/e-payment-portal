package mr.epayment.portal.subscriber;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chiffrement des identifiants officiels (NNI, NIF) en base : AES-256-GCM, IV aléatoire, format
 * « v1:base64(iv | texte chiffré | tag) ». Une empreinte HMAC-SHA256 (clé dérivée) permet de vérifier
 * l'unicité sans déchiffrer. La clé vient de la configuration (DATA_ENCRYPTION_KEY), jamais de la base.
 *
 * <p>Clé statique : le convertisseur JPA et l'entité sont créés par Hibernate, hors de Spring.
 */
@Component
public class SensitiveData {

    static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec encryptionKey;
    private static volatile SecretKeySpec hashKey;

    SensitiveData(@Value("${epayment.security.data-key}") String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key.trim());
        if (key.length != 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY doit faire 32 octets (256 bits) encodés en base64.");
        }
        encryptionKey = new SecretKeySpec(key, "AES");
        hashKey = new SecretKeySpec(hmac(new SecretKeySpec(key, "HmacSHA256"), "blind-index"), "HmacSHA256");
    }

    static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    /** Une valeur sans préfixe est une ancienne valeur en clair, en attente du rattrapage au démarrage. */
    static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Déchiffrement impossible : clé DATA_ENCRYPTION_KEY différente ?", e);
        }
    }

    /** Empreinte stable d'un identifiant (index d'unicité) ; null si la valeur est absente. */
    static String hash(String plain) {
        if (hashKey == null) {
            throw new IllegalStateException("Clé de chiffrement non initialisée.");
        }
        return plain == null ? null : HexFormat.of().formatHex(hmac(hashKey, plain));
    }

    private static SecretKeySpec requireKey() {
        if (encryptionKey == null) {
            throw new IllegalStateException("Clé de chiffrement non initialisée.");
        }
        return encryptionKey;
    }

    private static byte[] hmac(SecretKeySpec key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
