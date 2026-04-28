package com.say5.payflow.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.say5.payflow.domain.PaymentStatus;
import com.say5.payflow.domain.RefundStatus;
import com.say5.payflow.persistence.*;
import com.say5.payflow.service.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {
    private final WebhookEventRepo events;
    private final PaymentIntentRepo intents;
    private final RefundRepo refunds;
    private final AuditService audit;
    private final ObjectMapper om;

    public WebhookService(WebhookEventRepo events, PaymentIntentRepo intents,
                          ChargeRepo charges, RefundRepo refunds, AuditService audit,
                          ObjectMapper om) {
        this.events = events;
        this.intents = intents;
        this.refunds = refunds;
        this.audit = audit;
        this.om = om;
    }

    public record Ingested(boolean accepted, boolean duplicate, String error) {}

    /**
     * Ingest one verified Stripe event. Idempotent on
     * (provider, provider_event_id): the same event arriving twice
     * yields one durable side-effect.
     *
     * Threading: two simultaneous deliveries of the same event will both
     * read "no existing event" and race the insert. Whichever loses the
     * unique-constraint race surfaces as a {@link DataIntegrityViolationException}
     * out of {@link #insertEventReservation}; we catch it and treat the
     * loser as a duplicate, preserving the at-least-once contract without
     * double-applying side effects.
     *
     * Reconciliation failures surface as "error" status on the webhook
     * row but the method returns accepted=true so the provider sees a
     * 200 and doesn't pile up retries on us.
     */
    public Ingested ingest(String rawPayload) {
        JsonNode node;
        try {
            node = om.readTree(rawPayload);
        } catch (Exception e) {
            return new Ingested(false, false, "invalid json: " + e.getMessage());
        }

        String providerEventId = text(node, "id");
        String eventType = text(node, "type");
        if (providerEventId == null || eventType == null) {
            return new Ingested(false, false, "missing id or type");
        }

        WebhookEvent row = insertEventReservation(providerEventId, eventType, rawPayload);
        if (row == null) {
            // Pre-existing event or race lost — return duplicate without
            // mutating the original event's terminal status.
            return new Ingested(true, true, null);
        }

        try {
            dispatchAndCommit(eventType, node, row.getId());
            audit.record("webhook", providerEventId, "webhook." + eventType,
                "webhook_event", row.getId().toString(), null, "ok", null);
            return new Ingested(true, false, null);
        } catch (Exception e) {
            markEventError(row.getId(), e.getMessage());
            String safeDetail = "{}";
            try {
                safeDetail = om.writeValueAsString(Map.of("error",
                    e.getMessage() == null ? "" : e.getMessage()));
            } catch (Exception ignored) {}
            audit.record("webhook", providerEventId, "webhook." + eventType,
                "webhook_event", row.getId().toString(), null, "error", safeDetail);
            return new Ingested(true, false, e.getMessage());
        }
    }

    /**
     * Insert the event-id reservation. Returns the row on success, or
     * null when the event is already present (duplicate or concurrent
     * race).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEvent insertEventReservation(String providerEventId, String eventType,
                                               String rawPayload) {
        Optional<WebhookEvent> existing = events.findByProviderAndProviderEventId(
            "stripe", providerEventId);
        if (existing.isPresent()) {
            return null;
        }
        WebhookEvent row = new WebhookEvent(
            UUID.randomUUID(), "stripe", providerEventId, eventType, rawPayload, "received");
        try {
            events.saveAndFlush(row);
            return row;
        } catch (DataIntegrityViolationException race) {
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchAndCommit(String eventType, JsonNode node, UUID rowId) {
        JsonNode obj = node.path("data").path("object");
        switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentIntentTerminal(obj, PaymentStatus.SUCCEEDED);
            case "payment_intent.payment_failed" -> handlePaymentIntentTerminal(obj, PaymentStatus.FAILED);
            case "payment_intent.canceled" -> handlePaymentIntentTerminal(obj, PaymentStatus.CANCELED);
            case "charge.refunded" -> handleChargeRefunded(obj);
            default -> {
                // No-op — event row is still recorded for visibility.
            }
        }
        events.findById(rowId).ifPresent(WebhookEvent::markProcessed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventError(UUID rowId, String error) {
        events.findById(rowId).ifPresent(r -> r.markError(error));
    }

    private void handlePaymentIntentTerminal(JsonNode obj, PaymentStatus target) {
        String providerIntentId = text(obj, "id");
        if (providerIntentId == null) return;
        PaymentIntent pi = intents.findByProviderId(providerIntentId).orElse(null);
        if (pi == null) return;
        if (pi.getStatus() == target) return;
        // The provider is the authoritative ledger; even a terminal→terminal
        // change is honored. setStatus() would refuse the transition for
        // application code; here we go through the explicit override.
        pi.overrideStatusFromAuthority(target);
    }

    private void handleChargeRefunded(JsonNode obj) {
        String refundId = text(obj, "id");
        if (refundId == null) return;
        refunds.findByProviderRefundId(refundId).ifPresent(r -> r.setStatus(RefundStatus.SUCCEEDED));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
