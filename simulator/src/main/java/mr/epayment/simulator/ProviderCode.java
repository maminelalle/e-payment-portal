package mr.epayment.simulator;

/** Les trois fournisseurs imités. */
public enum ProviderCode {
    BANKILY("bankily"),
    MASRVI("masrvi"),
    VISA("visa");

    private final String configKey;

    ProviderCode(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
