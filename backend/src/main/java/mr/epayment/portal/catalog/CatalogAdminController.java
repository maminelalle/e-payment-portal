package mr.epayment.portal.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/moyens")
class CatalogAdminController {

    private final PaymentCatalog catalog;

    CatalogAdminController(PaymentCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<PaymentMethodView> list() {
        return catalog.findAll();
    }

    /** Fournisseurs auxquels rattacher un nouveau moyen (les non raccordés sont signalés). */
    @GetMapping("/fournisseurs")
    List<PaymentMethodView.Provider> providers() {
        return catalog.providers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentMethodView create(@Valid @RequestBody NewMethod request) {
        return catalog.create(request.code(), request.providerCode(), request.currency(), request.details());
    }

    @PutMapping("/{code}")
    PaymentMethodView update(@PathVariable String code, @Valid @RequestBody MethodDetails details) {
        return catalog.update(code, details);
    }

    @PatchMapping("/{code}/statut")
    PaymentMethodView changeStatus(@PathVariable String code, @Valid @RequestBody StatusChange request) {
        return catalog.changeStatus(code, request.status());
    }

    @PutMapping(value = "/{code}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PaymentMethodView changeLogo(@PathVariable String code, @RequestParam("file") MultipartFile file)
            throws IOException {
        return catalog.changeLogo(code, file.getBytes());
    }

    @DeleteMapping("/{code}/logo")
    PaymentMethodView removeLogo(@PathVariable String code) {
        return catalog.removeLogo(code);
    }

    record StatusChange(@NotNull PaymentMethodStatus status) {
    }

    record NewMethod(
            @NotBlank(message = "Le code est obligatoire.")
            @Pattern(regexp = "\\s*[A-Za-z][A-Za-z0-9_]{1,29}\\s*",
                    message = "2 à 30 caractères : lettres, chiffres et « _ », en commençant par une lettre.")
            String code,
            @NotBlank(message = "Choisissez le fournisseur.") String providerCode,
            @NotBlank(message = "La devise est obligatoire.")
            @Pattern(regexp = "[A-Za-z]{3}", message = "Code devise sur 3 lettres (MRU, EUR…).")
            String currency,
            @NotNull @Valid MethodDetails details) {
    }
}
