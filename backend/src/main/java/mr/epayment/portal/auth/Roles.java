package mr.epayment.portal.auth;

import java.util.Set;

/** Rôles du realm Keycloak {@code epayment}. */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String ABONNE = "ABONNE";

    static final Set<String> ALL = Set.of(ADMIN, ABONNE);

    private Roles() {
    }
}
