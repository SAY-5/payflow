package com.say5.payflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.say5.payflow.persistence.PaymentIntent;
import com.say5.payflow.service.IdempotencyService;
import com.say5.payflow.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payment-intents")
public class PaymentController {
    private final PaymentService payments;
    private final IdempotencyService idempotency;
    private final ObjectMapper om;

    public PaymentController(PaymentService payments, IdempotencyService idempotency, ObjectMapper om) {
        this.payments = payments;
        this.idempotency = idempotency;
        this.om = om;
    }

    public record CreateBody(
        UUID customerId,
        @NotNull @Min(1) Long amountCents,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        String description,
        Map<String, Object> metadata
    ) {}

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateBody body,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    HttpServletRequest req) throws Exception {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        if (merchantId == null) return ResponseEntity.status(401).build();
        if (key == null || key.isBlank()) {
            return problem(400, "Idempotency-Key header required");
        }

        String canonical = om.writeValueAsString(body);
        IdempotencyService.Result replayed;
        try {
            replayed = idempotency.reserveOrReplay(merchantId, key, canonical);
        } catch (IdempotencyService.Conflict c) {
            return ResponseEntity.status(409)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(Map.of("error", "request in progress"));
        } catch (IdempotencyService.MismatchedBody m) {
            return problem(422, "idempotency key was reused with a different body");
        }
        if (replayed != null) {
            return ResponseEntity.status(replayed.status())
                .header("Idempotency-Replayed", "true")
                .body(om.readValue(replayed.body(), Map.class));
        }

        var metaJson = body.metadata() == null ? "{}" : om.writeValueAsString(body.metadata());
        var created = payments.createAndConfirm(new PaymentService.CreateRequest(
            merchantId, body.customerId(), body.amountCents(),
            body.currency().toUpperCase(), body.description(), metaJson));

        Map<String, Object> resp = toDto(created.intent(), List.of(), List.of());
        String body2 = om.writeValueAsString(resp);
        idempotency.complete(merchantId, key, 201, body2);
        return ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, HttpServletRequest req) {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        return payments.find(merchantId, id)
            .map(pi -> ResponseEntity.ok((Object) toDto(pi, List.of(), List.of())))
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "not found")));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "25") int size,
                                  HttpServletRequest req) {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        Page<PaymentIntent> p = payments.list(merchantId, PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(Map.of(
            "page", page,
            "size", size,
            "total", p.getTotalElements(),
            "items", p.getContent().stream().map(pi -> toDto(pi, List.of(), List.of())).toList()
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID id, HttpServletRequest req) {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        try {
            return ResponseEntity.ok(toDto(payments.cancel(merchantId, id), List.of(), List.of()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(HttpServletRequest req) {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        return ResponseEntity.ok(payments.stats(merchantId));
    }

    private Map<String, Object> toDto(PaymentIntent pi, List<?> charges, List<?> refunds) {
        return Map.of(
            "id", pi.getId().toString(),
            "status", pi.getStatus().wire(),
            "amountCents", pi.getAmountCents(),
            "currency", pi.getCurrency(),
            "description", pi.getDescription() == null ? "" : pi.getDescription(),
            "providerId", pi.getProviderId() == null ? "" : pi.getProviderId(),
            "createdAt", pi.getCreatedAt().toString(),
            "updatedAt", pi.getUpdatedAt().toString()
        );
    }

    private ResponseEntity<?> problem(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of(
            "type", "about:blank",
            "title", msg,
            "status", status
        ));
    }
}
