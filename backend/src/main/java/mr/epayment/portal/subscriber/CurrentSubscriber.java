package mr.epayment.portal.subscriber;

import mr.epayment.portal.auth.SubscriberAccounts;
import mr.epayment.portal.shared.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abonné correspondant à l'utilisateur connecté. Toutes les API de l'espace abonné partent d'ici :
 * un abonné n'accède qu'à ses propres données, jamais à celles d'un autre identifiant passé en paramètre.
 */
@Service
public class CurrentSubscriber {

    private final SubscriberAccounts accounts;
    private final SubscriberRepository subscribers;

    CurrentSubscriber(SubscriberAccounts accounts, SubscriberRepository subscribers) {
        this.accounts = accounts;
        this.subscribers = subscribers;
    }

    @Transactional(readOnly = true)
    public SubscriberView require(Authentication authentication) {
        return accounts.accountIdOf(authentication.getName())
                .flatMap(subscribers::findByUserId)
                .map(Subscriber::toView)
                .orElseThrow(() -> new NotFoundException("Aucun abonné n'est rattaché à ce compte."));
    }
}
