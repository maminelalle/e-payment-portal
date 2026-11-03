package mr.epayment.simulator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Paiement simulé, quel que soit le fournisseur. Chaque contrôleur le présente au format de son API.
 * Mutable et partagé entre threads : toutes les écritures passent par des méthodes synchronisées.
 */
public final class SimPayment {

    public enum State { OPEN, PENDING, SUCCEEDED, FAILED, EXPIRED }

    private final ProviderCode provider;
    private final String id;
    private final String merchantReference;
    private final BigDecimal amount;
    private final String currency;
    private final String payerPhone;
    private final String description;
    private final String returnUrl;
    private final String cancelUrl;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Scenario scenario;
    private State state;
    private String failureCode;
    private String maskedPan;
    private String cardToken;
    private Instant updatedAt;

    SimPayment(ProviderCode provider, String id, String merchantReference, BigDecimal amount, String currency,
            String payerPhone, String description, String returnUrl, String cancelUrl, Scenario scenario,
            State initialState, Instant expiresAt) {
        this.provider = provider;
        this.id = id;
        this.merchantReference = merchantReference;
        this.amount = amount;
        this.currency = currency;
        this.payerPhone = payerPhone;
        this.description = description;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
        this.scenario = scenario;
        this.state = initialState;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
    }

    synchronized void resolve(State finalState, String failureCode) {
        if (state == State.SUCCEEDED || state == State.FAILED || state == State.EXPIRED) {
            return;
        }
        this.state = finalState;
        this.failureCode = failureCode;
        this.updatedAt = Instant.now();
    }

    /** Carte saisie sur la page hébergée : seuls le PAN masqué et un jeton sont conservés. */
    synchronized void cardSubmitted(String maskedPan, String cardToken, Scenario scenario) {
        this.maskedPan = maskedPan;
        this.cardToken = cardToken;
        this.scenario = scenario;
        this.state = State.PENDING;
        this.updatedAt = Instant.now();
    }

    /** Session carte non payée à temps. */
    synchronized boolean expireIfDue(Instant now) {
        if (state == State.OPEN && expiresAt != null && now.isAfter(expiresAt)) {
            state = State.EXPIRED;
            updatedAt = now;
            return true;
        }
        return false;
    }

    public ProviderCode provider() { return provider; }
    public String id() { return id; }
    public String merchantReference() { return merchantReference; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public String payerPhone() { return payerPhone; }
    public String description() { return description; }
    public String returnUrl() { return returnUrl; }
    public String cancelUrl() { return cancelUrl; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public synchronized Scenario scenario() { return scenario; }
    public synchronized State state() { return state; }
    public synchronized String failureCode() { return failureCode; }
    public synchronized String maskedPan() { return maskedPan; }
    public synchronized String cardToken() { return cardToken; }
    public synchronized Instant updatedAt() { return updatedAt; }
}
