package mr.epayment.portal.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    record StatusChange(@NotNull PaymentMethodStatus status) {
    }
}
