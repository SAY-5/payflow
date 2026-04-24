package com.say5.payflow.service;

import com.say5.payflow.domain.PaymentStatus;
import com.say5.payflow.persistence.*;
import com.say5.payflow.persistence.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentIntentRepo intents;
    private final ChargeRepo charges;
    private final CustomerRepo customers;
    private final StripeGateway gateway;
    private final AuditService audit;

    public PaymentService(PaymentIntentRepo intents, ChargeRepo charges, CustomerRepo customers,
                          StripeGateway gateway, AuditService audit) {
        this.intents = intents;
        this.charges = charges;
        this.customers = customers;
        this.gateway = gateway;
        this.audit = audit;
    }

    public record CreateRequest(UUID merchantId, UUID customerId, long amountCents,
                                String currency, String description, String metadataJson) {}

    public record CreateResult(PaymentIntent intent, Charge charge, boolean newlyCreated) {}

    /**
     * Create and immediately confirm a payment intent. Wrapped in a single
     * transaction so the intent, charge, and status updates commit as one.
     * Returns the final state.
     */
    @Transactional
    public CreateResult createAndConfirm(CreateRequest r) {
        UUID intentId = UUID.randomUUID();
        PaymentIntent intent = new PaymentIntent(
            intentId, r.merchantId, r.customerId, r.amountCents, r.currency,
            PaymentStatus.PROCESSING, r.description, r.metadataJson);
        intents.save(intent);

        UUID chargeId = UUID.randomUUID();
        int attempt = (int) charges.countByIntentId(intentId) + 1;
        Charge charge = new Charge(chargeId, intentId, attempt, "pending");
        charges.save(charge);

        String customerEmail = Optional.ofNullable(r.customerId)
            .flatMap(customers::findById)
            .map(Customer::getEmail)
            .orElse(null);

        StripeGateway.ConfirmResult cr = gateway.confirm(
            intentId, r.amountCents, r.currency, customerEmail);
        intent.setProviderId(cr.providerIntentId());
        if (cr.succeeded()) {
            charge.markSucceeded(cr.providerChargeId());
            intent.setStatus(PaymentStatus.SUCCEEDED);
        } else {
            charge.markFailed(cr.failureCode(), cr.failureMessage());
            intent.setStatus(PaymentStatus.FAILED);
        }

        audit.record("merchant", r.merchantId.toString(), "payment.create",
            "payment_intent", intentId.toString(), null,
            cr.succeeded() ? "ok" : "error",
            "{\"failure\":\"" + (cr.failureCode() == null ? "" : cr.failureCode()) + "\"}");

        return new CreateResult(intent, charge, true);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentIntent> find(UUID merchantId, UUID intentId) {
        return intents.findById(intentId)
            .filter(pi -> pi.getMerchantId().equals(merchantId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> list(UUID merchantId, Pageable p) {
        return intents.findByMerchantIdOrderByCreatedAtDesc(merchantId, p);
    }

    @Transactional
    public PaymentIntent cancel(UUID merchantId, UUID intentId) {
        PaymentIntent pi = find(merchantId, intentId)
            .orElseThrow(() -> new IllegalArgumentException("not found"));
        if (pi.getStatus().isTerminal() && pi.getStatus() != PaymentStatus.SUCCEEDED) {
            // Already canceled/failed — fine, return as-is.
            return pi;
        }
        if (pi.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("cannot cancel a succeeded payment; refund instead");
        }
        pi.setStatus(PaymentStatus.CANCELED);
        audit.record("merchant", merchantId.toString(), "payment.cancel",
            "payment_intent", intentId.toString(), null, "ok", null);
        return pi;
    }

    public record Stats(long succeededCount, long failedCount, long processingCount,
                        long succeededAmountCents) {}

    @Transactional(readOnly = true)
    public Stats stats(UUID merchantId) {
        return new Stats(
            intents.countByMerchantIdAndStatus(merchantId, "succeeded"),
            intents.countByMerchantIdAndStatus(merchantId, "failed"),
            intents.countByMerchantIdAndStatus(merchantId, "processing"),
            intents.sumSucceededAmount(merchantId));
    }
}
