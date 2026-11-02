package mr.epayment.portal.auth;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profil de l'utilisateur connecté, commun aux deux espaces. */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserAccountService userAccountService;

    MeController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    CurrentUser me(JwtAuthenticationToken authentication) {
        return userAccountService.currentUser(authentication);
    }

    @PostMapping("/connexion")
    CurrentUser connexion(JwtAuthenticationToken authentication) {
        return userAccountService.recordLogin(authentication);
    }
}
