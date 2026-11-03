package mr.epayment.portal.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mr.epayment.portal.contract.ContractTemplate.TemplateView;

/** Contrat tel qu'exposé par l'API d'administration. */
public record ContractView(
        UUID id,
        String reference,
        ContractStatus status,
        Party subscriber,
        TemplateView template,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal commissionRate,
        List<Method> methods,
        String pdfSha256,
        Instant sentAt,
        String signedPdfSha256,
        Instant signedAt,
        Instant activatedAt,
        Instant terminatedAt,
        String terminationReason,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public record Party(UUID id, String reference, String displayName, String email, String status) {
    }

    public record Method(String code, String name, String shortLabel, String color, String logoUrl, String category,
            BigDecimal perTransactionLimit, BigDecimal monthlyLimit) {
    }
}
