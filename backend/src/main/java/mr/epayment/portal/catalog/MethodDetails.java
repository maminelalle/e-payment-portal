package mr.epayment.portal.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Ce que l'admin peut modifier sur un moyen de paiement, à la création comme ensuite. */
record MethodDetails(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 100, message = "100 caractères au maximum.")
        String name,

        /** Pastille affichée sans logo (« BK », « VI »). */
        @NotBlank(message = "La pastille est obligatoire.")
        @Pattern(regexp = "[A-Za-z0-9]{1,4}", message = "1 à 4 lettres ou chiffres.")
        String shortLabel,

        @NotBlank(message = "La couleur est obligatoire.")
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "Couleur au format #RRGGBB.")
        String color,

        @Size(max = 200, message = "200 caractères au maximum.")
        String description,

        @Size(max = 500, message = "500 caractères au maximum.")
        String instructions,

        @Size(max = 1000, message = "1 000 caractères au maximum.")
        String conditions) {

    MethodDetails {
        name = trimToNull(name);
        shortLabel = shortLabel == null ? null : shortLabel.trim().toUpperCase(Locale.ROOT);
        color = color == null ? null : color.trim().toUpperCase(Locale.ROOT);
        description = trimToNull(description);
        instructions = trimToNull(instructions);
        conditions = trimToNull(conditions);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
