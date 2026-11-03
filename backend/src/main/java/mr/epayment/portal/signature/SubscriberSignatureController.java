package mr.epayment.portal.signature;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import mr.epayment.portal.contract.ContractView;
import mr.epayment.portal.signature.SignatureService.OtpChallenge;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signature du contrat depuis l'espace abonné (maquette B2). */
@RestController
@RequestMapping("/api/abonne/contrat/signature")
class SubscriberSignatureController {

    private final SignatureService service;

    SubscriberSignatureController(SignatureService service) {
        this.service = service;
    }

    @PostMapping
    OtpChallenge requestCode(Authentication authentication) {
        return service.requestCode(authentication);
    }

    @PostMapping("/{id}/confirmation")
    ContractView confirm(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody Confirmation body,
            HttpServletRequest request) {
        return service.confirm(authentication, id, body.code(), request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT));
    }

    @ExceptionHandler(OtpRejectedException.class)
    ProblemDetail rejected(OtpRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setProperty("remainingAttempts", e.remainingAttempts());
        return problem;
    }

    record Confirmation(
            @NotBlank(message = "Saisissez le code reçu.")
            @Pattern(regexp = "\\d{6}", message = "Le code comporte 6 chiffres.")
            String code) {
    }
}
