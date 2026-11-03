package mr.epayment.portal.subscriber;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Champs modifiables d'un abonné. Le téléphone est normalisé avant validation :
 * « 22 00 00 01 », « 0022222000001 » et « +222 22 00 00 01 » donnent tous « +22222000001 ».
 */
record SubscriberDetails(
        @NotBlank(message = "Le nom ou la raison sociale est obligatoire.")
        @Size(max = 200, message = "200 caractères au maximum.")
        String displayName,

        @Pattern(regexp = "\\d{10}", message = "Le NNI comporte 10 chiffres.")
        String nni,

        @Pattern(regexp = "\\d{8}", message = "Le NIF comporte 8 chiffres.")
        String nif,

        @NotBlank(message = "Le téléphone est obligatoire.")
        @Pattern(regexp = "\\+222[234]\\d{7}",
                message = "Numéro mauritanien attendu : +222 suivi de 8 chiffres commençant par 2, 3 ou 4.")
        String phone,

        @Size(max = 300, message = "300 caractères au maximum.")
        String address) {

    SubscriberDetails {
        displayName = trimToNull(displayName);
        nni = trimToNull(nni);
        nif = trimToNull(nif);
        phone = normalizePhone(phone);
        address = trimToNull(address);
    }

    static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s.\\-()]", "");
        if (digits.startsWith("00222")) {
            return "+" + digits.substring(2);
        }
        if (digits.matches("\\d{8}")) {
            return "+222" + digits;
        }
        if (digits.matches("222\\d{8}")) {
            return "+" + digits;
        }
        return digits;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
