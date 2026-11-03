package mr.epayment.simulator;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs au format simple d'une API de fournisseur. */
@RestControllerAdvice
class SimulatorErrors {

    @ExceptionHandler(PaymentEngine.IdempotencyConflictException.class)
    ResponseEntity<Map<String, String>> idempotency(PaymentEngine.IdempotencyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "idempotency_conflict",
                "message", e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
    }
}
