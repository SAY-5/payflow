package com.say5.payflow.service;

import com.say5.payflow.domain.PaymentStatus;
import com.say5.payflow.domain.RefundStatus;
import com.say5.payflow.persistence.*;
import com.say5.payflow.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefundService {
    private final PaymentIntentRepo intents;
    private final ChargeRepo charges;
    private final RefundRepo refunds;
    private final StripeGateway gateway;
    private final AuditService audit;

    public RefundService(PaymentIntentRepo intents, ChargeRepo charges, RefundRepo refunds,
                         StripeGateway gateway, AuditService audit) {
        this.intents = intents;
        this.charges = charges;
        this.refunds = refunds;
        this.gateway = gateway;
        this.audit = audit;
    }

    public record CreateRequest(UUID merchantId, UUID intentId, Long amountCents, String reason) {}

    @Transactional
    public Refund create(CreateRequest r) {
        PaymentIntent pi = intents.findById(r.intentId)
            .orElseThrow(() -> new IllegalArgumentException("intent not found"));
        if (!pi.getMerchantId().equals(r.merchantId)) {
            throw new IllegalArgumentException("intent not found");
        }
        if (pi.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                "cannot refund a payment in status " + pi.getStatus().wire());
        }

        long totalRefunded = refunds.sumSucceededAmountForIntent(pi.getId());
        long requested = r.amountCents != null ? r.amountCents : (pi.getAmountCents() - totalRefunded);
        if (requested <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        if (totalRefunded + requested > pi.getAmountCents()) {
            throw new IllegalStateException("refund would exceed original amount");
        }

        List<Charge> succeededCharges = charges.findByIntentIdOrderByAttemptNoAsc(pi.getId())
            .stream()
            .filter(c -> "succeeded".equals(c.getStatus()))
            .toList();
        if (succeededCharges.isEmpty()) {
            throw new IllegalStateException("no succeeded charge to refund");
        }
        String providerChargeId = succeededCharges.get(0).getProviderChargeId();

        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(refundId, pi.getId(), requested, RefundStatus.PENDING, r.reason);
        refunds.save(refund);

        StripeGateway.RefundResult gr = gateway.refund(providerChargeId, requested, r.reason);
        if (gr.succeeded()) {
            refund.setProviderRefundId(gr.providerRefundId());
            refund.setStatus(RefundStatus.SUCCEEDED);
        } else {
            refund.setStatus(RefundStatus.FAILED);
        }

        audit.record("merchant", r.merchantId.toString(), "refund.create",
            "refund", refundId.toString(), null,
            gr.succeeded() ? "ok" : "error",
            gr.failureCode() == null ? null : "{\"failure\":\"" + gr.failureCode() + "\"}");

        return refund;
    }

    @Transactional(readOnly = true)
    public Optional<Refund> find(UUID merchantId, UUID refundId) {
        return refunds.findById(refundId).filter(rf ->
            intents.findById(rf.getIntentId()).map(pi -> pi.getMerchantId().equals(merchantId))
                .orElse(false));
    }

    @Transactional(readOnly = true)
    public List<Refund> listForIntent(UUID merchantId, UUID intentId) {
        PaymentIntent pi = intents.findById(intentId)
            .filter(x -> x.getMerchantId().equals(merchantId))
            .orElseThrow(() -> new IllegalArgumentException("intent not found"));
        return refunds.findByIntentIdOrderByCreatedAtDesc(pi.getId());
    }
}
