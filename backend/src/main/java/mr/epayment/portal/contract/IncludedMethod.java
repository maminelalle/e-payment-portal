package mr.epayment.portal.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

/** Moyen de paiement inclus dans un contrat, avec ses plafonds (table contract_payment_method). */
@Embeddable
class IncludedMethod {

    @Column(name = "payment_method_id", nullable = false)
    private UUID paymentMethodId;

    @Column(name = "per_transaction_limit", nullable = false)
    private BigDecimal perTransactionLimit;

    @Column(name = "monthly_limit", nullable = false)
    private BigDecimal monthlyLimit;

    protected IncludedMethod() {
    }

    IncludedMethod(UUID paymentMethodId, BigDecimal perTransactionLimit, BigDecimal monthlyLimit) {
        this.paymentMethodId = paymentMethodId;
        this.perTransactionLimit = perTransactionLimit;
        this.monthlyLimit = monthlyLimit;
    }

    UUID paymentMethodId() {
        return paymentMethodId;
    }

    BigDecimal perTransactionLimit() {
        return perTransactionLimit;
    }

    BigDecimal monthlyLimit() {
        return monthlyLimit;
    }
}
