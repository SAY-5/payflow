package com.say5.payflow.service;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic fake gateway. Rules:
 *   - customer email containing "@fail." → failure with code "card_declined"
 *   - amount > 1_000_000 cents  → "amount_too_large"
 *   - refunds always succeed unless the original charge id starts with "ch_fail_"
 *
 * Used by default (dev + test). In prod the real StripeGateway bean replaces it.
 */
@Component
@Profile("!prod")
public class InMemoryStripeGateway implements StripeGateway {

    @Override
    public ConfirmResult confirm(UUID intentId, long amountCents, String currency, String customerEmail) {
        String providerIntentId = "pi_" + intentId.toString().replace("-", "");
        if (customerEmail != null && customerEmail.contains("@fail.")) {
            return new ConfirmResult(providerIntentId, null, false,
                "card_declined", "Your card was declined.");
        }
        if (amountCents > 1_000_000L) {
            return new ConfirmResult(providerIntentId, null, false,
                "amount_too_large", "Charge exceeds permitted amount.");
        }
        return new ConfirmResult(
            providerIntentId,
            "ch_" + intentId.toString().replace("-", ""),
            true, null, null);
    }

    @Override
    public RefundResult refund(String providerChargeId, long amountCents, String reason) {
        if (providerChargeId == null || providerChargeId.startsWith("ch_fail_")) {
            return new RefundResult(null, false, "charge_not_refundable",
                "Charge cannot be refunded.");
        }
        return new RefundResult(
            "re_" + UUID.randomUUID().toString().replace("-", ""),
            true, null, null);
    }
}
