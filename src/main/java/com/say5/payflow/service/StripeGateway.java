package com.say5.payflow.service;

import java.util.UUID;

/**
 * Gateway façade to the payment provider. In production this calls the
 * Stripe SDK; for tests and the self-contained demo we use
 * {@link InMemoryStripeGateway}, which behaves deterministically based
 * on the customer's email address (see that class for the rules).
 */
public interface StripeGateway {

    record ConfirmResult(String providerIntentId, String providerChargeId, boolean succeeded,
                         String failureCode, String failureMessage) {}

    ConfirmResult confirm(UUID intentId, long amountCents, String currency, String customerEmail);

    record RefundResult(String providerRefundId, boolean succeeded,
                        String failureCode, String failureMessage) {}

    RefundResult refund(String providerChargeId, long amountCents, String reason);
}
