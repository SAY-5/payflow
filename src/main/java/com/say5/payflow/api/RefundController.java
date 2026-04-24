package com.say5.payflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.say5.payflow.persistence.Refund;
import com.say5.payflow.service.IdempotencyService;
import com.say5.payflow.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/refunds")
public class RefundController {
    private final RefundService refunds;
    private final IdempotencyService idempotency;
    private final ObjectMapper om;

    public RefundController(RefundService refunds, IdempotencyService idempotency, ObjectMapper om) {
        this.refunds = refunds;
        this.idempotency = idempotency;
        this.om = om;
    }

    public record CreateBody(@NotNull UUID paymentIntentId, Long amountCents, String reason) {}

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateBody body,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    HttpServletRequest req) throws Exception {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        if (merchantId == null) return ResponseEntity.status(401).build();
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header required"));
        }
        String canonical = om.writeValueAsString(body);
        IdempotencyService.Result replay;
        try {
            replay = idempotency.reserveOrReplay(merchantId, key, canonical);
        } catch (IdempotencyService.Conflict c) {
            return ResponseEntity.status(409).header(HttpHeaders.RETRY_AFTER, "5")
                .body(Map.of("error", "request in progress"));
        } catch (IdempotencyService.MismatchedBody m) {
            return ResponseEntity.status(422).body(Map.of("error", "key reused with different body"));
        }
        if (replay != null) {
            return ResponseEntity.status(replay.status())
                .header("Idempotency-Replayed", "true")
                .body(om.readValue(replay.body(), Map.class));
        }

        try {
            Refund r = refunds.create(new RefundService.CreateRequest(
                merchantId, body.paymentIntentId(), body.amountCents(), body.reason()));
            Map<String, Object> resp = dto(r);
            idempotency.complete(merchantId, key, 201, om.writeValueAsString(resp));
            return ResponseEntity.status(201).body(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> resp = Map.of("error", e.getMessage());
            idempotency.complete(merchantId, key, 404, om.writeValueAsString(resp));
            return ResponseEntity.status(404).body(resp);
        } catch (IllegalStateException e) {
            Map<String, Object> resp = Map.of("error", e.getMessage());
            idempotency.complete(merchantId, key, 409, om.writeValueAsString(resp));
            return ResponseEntity.status(409).body(resp);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id, HttpServletRequest req) {
        UUID merchantId = (UUID) req.getAttribute("merchantId");
        return refunds.find(merchantId, id)
            .map(r -> ResponseEntity.ok((Object) dto(r)))
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "not found")));
    }

    private Map<String, Object> dto(Refund r) {
        return Map.of(
            "id", r.getId().toString(),
            "paymentIntentId", r.getIntentId().toString(),
            "amountCents", r.getAmountCents(),
            "status", r.getStatus().wire(),
            "reason", r.getReason() == null ? "" : r.getReason(),
            "providerRefundId", r.getProviderRefundId() == null ? "" : r.getProviderRefundId(),
            "createdAt", r.getCreatedAt().toString(),
            "updatedAt", r.getUpdatedAt().toString()
        );
    }
}
