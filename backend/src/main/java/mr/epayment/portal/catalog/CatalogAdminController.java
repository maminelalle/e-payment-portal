package mr.epayment.portal.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
