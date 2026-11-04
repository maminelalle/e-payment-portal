package mr.epayment.portal.auth;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

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

    @PostMapping("/deconnexion")
    void deconnexion(JwtAuthenticationToken authentication) {
        userAccountService.recordLogout(authentication);
    }

    @PutMapping
    CurrentUser update(JwtAuthenticationToken authentication, @RequestBody UpdateCurrentUser request) {
        return userAccountService.updateCurrentUser(authentication, request.email(), request.fullName());
    }

    record UpdateCurrentUser(String email, String fullName) {
    }
}
