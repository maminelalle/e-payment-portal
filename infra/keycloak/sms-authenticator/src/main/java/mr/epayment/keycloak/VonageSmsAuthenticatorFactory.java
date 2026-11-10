package mr.epayment.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public final class VonageSmsAuthenticatorFactory implements AuthenticatorFactory {
    public static final String ID = "vonage-sms-otp";
    private static final VonageSmsAuthenticator SINGLETON = new VonageSmsAuthenticator();

    @Override public String getId() { return ID; }
    @Override public String getReferenceCategory() { return "sms-otp"; }
    @Override public boolean isConfigurable() { return false; }
    @Override public boolean isUserSetupAllowed() { return false; }
    @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }
    @Override public String getDisplayType() { return "Vonage WhatsApp OTP"; }
    @Override public String getHelpText() { return "Sends and verifies an administrator OTP through Vonage WhatsApp."; }
    @Override public List<ProviderConfigProperty> getConfigProperties() { return List.of(); }
    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
    @Override public void init(Config.Scope config) { }
    @Override public void postInit(KeycloakSessionFactory factory) { }
    @Override public void close() { }
}
