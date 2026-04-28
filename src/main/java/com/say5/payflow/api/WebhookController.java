package com.say5.payflow.api;

import com.say5.payflow.config.AppProperties;
import com.say5.payflow.persistence.Merchant;
import com.say5.payflow.persistence.MerchantRepo;
import com.say5.payflow.webhook.StripeSignature;
import com.say5.payflow.webhook.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stripe-compatible webhook receiver.
 *
 * Endpoint shape: ``/webhooks/stripe/{merchantId}``. Each merchant
 * configures their Stripe webhook to post to their own URL (Stripe's
 * recommended pattern), so we know exactly which webhook secret to
 * verify against and never iterate the merchant table per request.
 *
 * The legacy unscoped path ``/webhooks/stripe`` is retained as a
 * deprecated convenience for the demo merchant only — production
 * deployments should disable it via the ``payflow.webhook.allow-legacy``
 * property (default false in prod profile).
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    private final WebhookService svc;
    private final MerchantRepo merchants;
    private final AppProperties props;

    public WebhookController(WebhookService svc, MerchantRepo merchants, AppProperties props) {
        this.svc = svc;
        this.merchants = merchants;
        this.props = props;
    }

    @PostMapping(value = "/stripe/{merchantId}", consumes = "*/*")
    public ResponseEntity<?> stripeFor(@PathVariable UUID merchantId,
                                       HttpServletRequest req,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String sig)
            throws IOException {
        Optional<Merchant> merchant = merchants.findById(merchantId);
        if (merchant.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "unknown merchant"));
        }
        byte[] raw = req.getInputStream().readAllBytes();
        if (!StripeSignature.verify(sig, raw, merchant.get().getWebhookSecret(),
                Instant.now().getEpochSecond(), props.getWebhook().getMaxAgeSeconds())) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid signature"));
        }
        return finishIngest(raw);
    }

    /**
     * Legacy un-scoped endpoint. Tries each merchant's secret in turn —
     * leaks timing information on multi-merchant installs. Disabled in
     * the prod profile via {@link com.say5.payflow.config.AppProperties}.
     */
    @PostMapping(value = "/stripe", consumes = "*/*")
    public ResponseEntity<?> stripeLegacy(HttpServletRequest req,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String sig)
            throws IOException {
        if (!props.getWebhook().isAllowLegacy()) {
            return ResponseEntity.status(404).body(Map.of(
                "error",
                "legacy unscoped /webhooks/stripe is disabled — use /webhooks/stripe/{merchantId}"));
        }
        byte[] raw = req.getInputStream().readAllBytes();
        long now = Instant.now().getEpochSecond();
        int maxAge = props.getWebhook().getMaxAgeSeconds();
        boolean verified = false;
        for (Merchant m : merchants.findAll()) {
            if (StripeSignature.verify(sig, raw, m.getWebhookSecret(), now, maxAge)) {
                verified = true;
                break;
            }
        }
        if (!verified) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid signature"));
        }
        return finishIngest(raw);
    }

    private ResponseEntity<?> finishIngest(byte[] raw) {
        WebhookService.Ingested ingested = svc.ingest(new String(raw, StandardCharsets.UTF_8));
        if (!ingested.accepted()) {
            return ResponseEntity.status(400).body(Map.of("error", ingested.error()));
        }
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "duplicate", ingested.duplicate()
        ));
    }
}
