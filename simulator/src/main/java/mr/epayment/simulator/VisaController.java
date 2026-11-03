package mr.epayment.simulator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mr.epayment.simulator.PaymentEngine.NewPayment;
import mr.epayment.simulator.SimPayment.State;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Passerelle carte imitée : le portail crée une session, redirige l'abonné vers la page hébergée ici,
 * puis attend le webhook. Le numéro de carte et le CVV ne quittent jamais cette page (périmètre PCI DSS
 * du portail réduit au minimum) ; seuls un PAN masqué et un jeton sont renvoyés.
 */
@RestController
class VisaController {

    private final PaymentEngine engine;
    private final SimulatorProperties properties;

    VisaController(PaymentEngine engine, SimulatorProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @PostMapping("/visa-gateway/api/v1/checkout-sessions")
    ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        SimPayment session = engine.create(new NewPayment(ProviderCode.VISA, "cs_", request.orderReference(),
                request.amount(), request.currency(), null, null, request.returnUrl(), request.cancelUrl(),
                Scenario.SUCCESS), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(session));
    }

    @GetMapping("/visa-gateway/api/v1/checkout-sessions/{sessionId}")
    ResponseEntity<Map<String, Object>> get(@PathVariable String sessionId) {
        return engine.find(ProviderCode.VISA, sessionId)
                .map(s -> ResponseEntity.ok(view(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Page de paiement hébergée, ouverte par le navigateur de l'abonné. */
    @GetMapping(value = "/visa-gateway/pay/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> page(@PathVariable String sessionId) {
        return engine.find(ProviderCode.VISA, sessionId)
                .map(s -> ResponseEntity.ok(s.state() == State.OPEN ? form(s, null) : closed(s)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(page("Session inconnue", "")));
    }

    @PostMapping(value = "/visa-gateway/pay/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> pay(@PathVariable String sessionId, @RequestParam String cardNumber,
            @RequestParam String expiry, @RequestParam String cvc, @RequestParam(defaultValue = "") String holder) {
        SimPayment session = engine.find(ProviderCode.VISA, sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(page("Session inconnue", ""));
        }
        if (session.state() != State.OPEN) {
            return ResponseEntity.ok(closed(session));
        }
        String pan = cardNumber.replaceAll("\\s", "");
        if (!pan.matches("\\d{16}") || !expiry.matches("\\d{2}/\\d{2}") || !cvc.matches("\\d{3}")) {
            return ResponseEntity.badRequest().body(form(session, "Vérifiez le numéro de carte, la date et le cryptogramme."));
        }
        String masked = pan.substring(0, 6) + "******" + pan.substring(12);
        engine.cardSubmitted(session, masked, "tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                Scenario.ofTestCard(pan));
        // Retour vers le portail : le paiement y est « en cours » jusqu'au webhook (le retour n'est pas une preuve).
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(returnUri(session.returnUrl(), session)).build();
    }

    @GetMapping("/visa-gateway/pay/{sessionId}/annuler")
    ResponseEntity<Void> cancel(@PathVariable String sessionId) {
        return engine.find(ProviderCode.VISA, sessionId)
                .map(s -> ResponseEntity.status(HttpStatus.SEE_OTHER).location(returnUri(s.cancelUrl(), s))
                        .<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    static String status(SimPayment p) {
        return switch (p.state()) {
            case SUCCEEDED -> "AUTHORIZED";
            case FAILED -> "DECLINED";
            case EXPIRED -> "EXPIRED";
            default -> "OPEN";
        };
    }

    static String declineReason(SimPayment p) {
        if (p.state() != State.FAILED) {
            return null;
        }
        return "INSUFFICIENT_FUNDS".equals(p.failureCode()) ? "insufficient_funds" : "do_not_honor";
    }

    private Map<String, Object> view(SimPayment s) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sessionId", s.id());
        view.put("orderReference", s.merchantReference());
        view.put("redirectUrl", properties.publicUrl() + "/visa-gateway/pay/" + s.id());
        view.put("status", status(s));
        view.put("amount", s.amount());
        view.put("currency", s.currency());
        view.put("card", s.maskedPan() == null ? null : Map.of("brand", "VISA", "maskedPan", s.maskedPan(),
                "token", s.cardToken()));
        view.put("declineReason", declineReason(s));
        view.put("expiresAt", s.expiresAt().toString());
        return view;
    }

    private static URI returnUri(String url, SimPayment s) {
        return UriComponentsBuilder.fromUriString(url).queryParam("sessionId", s.id()).build().toUri();
    }

    private static String form(SimPayment s, String error) {
        String amount = HtmlUtils.htmlEscape(s.amount().toPlainString() + " " + s.currency());
        String reference = HtmlUtils.htmlEscape(s.merchantReference());
        return page("Paiement par carte", """
                <p class="amount">%s</p>
                <p class="muted">Commande %s · Portail E-Payment</p>
                %s
                <form method="post" autocomplete="off">
                  <label>Numéro de carte<input name="cardNumber" inputmode="numeric" placeholder="4111 1111 1111 1111" required></label>
                  <div class="row">
                    <label>Expiration<input name="expiry" placeholder="12/28" required></label>
                    <label>Cryptogramme<input name="cvc" inputmode="numeric" placeholder="123" required></label>
                  </div>
                  <label>Titulaire<input name="holder" placeholder="NOM PRÉNOM"></label>
                  <button type="submit">Payer %s</button>
                </form>
                <p><a href="/visa-gateway/pay/%s/annuler">Annuler et revenir au portail</a></p>
                <details><summary>Cartes de test</summary>
                  <p>4111 1111 1111 1111 : accepté · 4000 0000 0000 0002 : refusé<br>
                  4000 0000 0000 9995 : fonds insuffisants · 4000 0000 0000 3220 : 3-D Secure (confirmation différée)</p>
                </details>""".formatted(amount, reference,
                error == null ? "" : "<p class=\"error\">" + HtmlUtils.htmlEscape(error) + "</p>", amount, s.id()));
    }

    private static String closed(SimPayment s) {
        return page("Session terminée", "<p>Cette session de paiement est terminée (" + status(s) + ").</p>");
    }

    private static String page(String title, String body) {
        return """
                <!doctype html><html lang="fr"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"><title>%s · Passerelle simulée</title>
                <style>
                  body{font-family:system-ui,sans-serif;background:#f1f5f9;margin:0;display:flex;justify-content:center;padding:40px 16px}
                  main{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:28px;width:100%%;max-width:420px}
                  .brand{color:#7c3aed;font-weight:700;letter-spacing:1px;font-size:12px}
                  h1{font-size:20px;margin:8px 0 16px}.amount{font-size:28px;font-weight:700;margin:0}
                  .muted{color:#64748b;margin-top:4px}label{display:block;margin-top:14px;font-size:14px}
                  input{display:block;width:100%%;box-sizing:border-box;margin-top:6px;padding:10px;border:1px solid #cbd5e1;border-radius:8px;font-size:16px}
                  .row{display:grid;grid-template-columns:1fr 1fr;gap:12px}
                  button{margin-top:20px;width:100%%;padding:12px;border:0;border-radius:8px;background:#7c3aed;color:#fff;font-size:16px;cursor:pointer}
                  .error{background:#fee2e2;color:#b91c1c;padding:10px;border-radius:8px}a{color:#7c3aed}
                  details{margin-top:16px;font-size:13px;color:#475569}
                </style></head>
                <body><main><div class="brand">PASSERELLE BANCAIRE · SIMULATEUR</div><h1>%s</h1>%s</main></body></html>"""
                .formatted(title, title, body);
    }

    record Request(
            @NotBlank String orderReference,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String returnUrl,
            @NotBlank String cancelUrl) {
    }
}
