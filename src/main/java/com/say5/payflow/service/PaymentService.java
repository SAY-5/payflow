package com.say5.payflow.service;

import com.say5.payflow.domain.PaymentStatus;
import com.say5.payflow.persistence.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
     * Create-and-confirm pipeline split into three bounded transactions
     * so the gateway call (potentially a slow network round trip to a
     * real payment provider) does NOT hold a JDBC connection from the
     * pool while waiting for the network.
     *
     *   1. persistPending(...)  — open tx, write intent + charge as
     *      PROCESSING/pending, commit.
     *   2. gateway.confirm(...) — out-of-tx network call, may take seconds.
     *   3. recordOutcome(...)   — open tx, attach providerId, transition
     *      intent to SUCCEEDED/FAILED, write audit row.
     *
     * If the process crashes between (2) and (3), the intent stays in
     * PROCESSING and a reconciliation job (or the next inbound
     * payment_intent.* webhook) will close it out.
     */
    public CreateResult createAndConfirm(CreateRequest r) {
        UUID intentId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        String customerEmail = persistPending(intentId, chargeId, r);

        StripeGateway.ConfirmResult cr = gateway.confirm(
            intentId, r.amountCents(), r.currency(), customerEmail);

        return recordOutcome(intentId, chargeId, r, cr);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String persistPending(UUID intentId, UUID chargeId, CreateRequest r) {
        PaymentIntent intent = new PaymentIntent(
            intentId, r.merchantId(), r.customerId(), r.amountCents(), r.currency(),
            PaymentStatus.PROCESSING, r.description(), r.metadataJson());
        intents.save(intent);
        int attempt = (int) charges.countByIntentId(intentId) + 1;
        Charge charge = new Charge(chargeId, intentId, attempt, "pending");
        charges.save(charge);
        return Optional.ofNullable(r.customerId())
            .flatMap(customers::findById)
            .map(Customer::getEmail)
            .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateResult recordOutcome(UUID intentId, UUID chargeId, CreateRequest r,
                                      StripeGateway.ConfirmResult cr) {
        PaymentIntent intent = intents.findById(intentId)
            .orElseThrow(() -> new IllegalStateException("intent vanished mid-flow"));
        Charge charge = charges.findById(chargeId)
            .orElseThrow(() -> new IllegalStateException("charge vanished mid-flow"));
        intent.setProviderId(cr.providerIntentId());
        if (cr.succeeded()) {
            charge.markSucceeded(cr.providerChargeId());
            intent.setStatus(PaymentStatus.SUCCEEDED);
        } else {
            charge.markFailed(cr.failureCode(), cr.failureMessage());
            intent.setStatus(PaymentStatus.FAILED);
        }
        audit.record("merchant", r.merchantId().toString(), "payment.create",
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
