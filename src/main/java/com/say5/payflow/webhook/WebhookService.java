package com.say5.payflow.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.say5.payflow.domain.PaymentStatus;
import com.say5.payflow.domain.RefundStatus;
import com.say5.payflow.persistence.*;
import com.say5.payflow.persistence.*;
import com.say5.payflow.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {
    private final WebhookEventRepo events;
    private final PaymentIntentRepo intents;
    private final ChargeRepo charges;
    private final RefundRepo refunds;
    private final AuditService audit;
    private final ObjectMapper om;

    public WebhookService(WebhookEventRepo events, PaymentIntentRepo intents,
                          ChargeRepo charges, RefundRepo refunds, AuditService audit,
                          ObjectMapper om) {
        this.events = events;
        this.intents = intents;
        this.charges = charges;
        this.refunds = refunds;
        this.audit = audit;
        this.om = om;
    }

    public record Ingested(boolean accepted, boolean duplicate, String error) {}

    /**
     * Ingest one verified Stripe event. Idempotent on
     * (provider, provider_event_id): the same event arriving twice is a
     * no-op. Reconciliation failures surface as "error" status on the
     * webhook row but the method returns accepted=true so the provider
     * sees a 200 and doesn't pile up retries on us.
     */
    @Transactional
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

        Optional<WebhookEvent> existing = events.findByProviderAndProviderEventId("stripe", providerEventId);
        if (existing.isPresent()) {
            existing.get().markDuplicate();
            return new Ingested(true, true, null);
        }

        WebhookEvent row = new WebhookEvent(
            UUID.randomUUID(), "stripe", providerEventId, eventType, rawPayload, "received");
        events.save(row);

        try {
            dispatch(eventType, node);
            row.markProcessed();
            audit.record("webhook", providerEventId, "webhook." + eventType,
                "webhook_event", row.getId().toString(), null, "ok", null);
            return new Ingested(true, false, null);
        } catch (Exception e) {
            row.markError(e.getMessage());
            audit.record("webhook", providerEventId, "webhook." + eventType,
                "webhook_event", row.getId().toString(), null, "error",
                "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
            return new Ingested(true, false, e.getMessage());
        }
    }

    private void dispatch(String eventType, JsonNode node) {
        JsonNode obj = node.path("data").path("object");
        switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentIntentTerminal(obj, PaymentStatus.SUCCEEDED);
            case "payment_intent.payment_failed" -> handlePaymentIntentTerminal(obj, PaymentStatus.FAILED);
            case "payment_intent.canceled" -> handlePaymentIntentTerminal(obj, PaymentStatus.CANCELED);
            case "charge.refunded" -> handleChargeRefunded(obj);
            default -> {
                // No-op — we still insert the event row for visibility.
            }
        }
    }

    private void handlePaymentIntentTerminal(JsonNode obj, PaymentStatus target) {
        String providerIntentId = text(obj, "id");
        if (providerIntentId == null) return;
        PaymentIntent pi = intents.findByProviderId(providerIntentId).orElse(null);
        if (pi == null) return; // event for an intent we didn't originate — ignore.
        if (pi.getStatus() == target) return;
        if (pi.getStatus().isTerminal() && pi.getStatus() != target) {
            // Stripe overrode us; trust Stripe (the authoritative ledger).
            pi.setStatus(target);
        } else {
            pi.setStatus(target);
        }
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
