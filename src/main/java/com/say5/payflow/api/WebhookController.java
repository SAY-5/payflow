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

    @PostMapping(value = "/stripe", consumes = "*/*")
    public ResponseEntity<?> stripe(HttpServletRequest req,
                                    @RequestHeader(value = "Stripe-Signature", required = false) String sig)
            throws IOException {
        byte[] raw = req.getInputStream().readAllBytes();

        // Webhook secret is merchant-scoped. In a real impl we'd
        // route by payment intent → merchant. For this reference we
        // verify against all known merchants' secrets; in prod you'd
        // configure one webhook endpoint per merchant (Stripe's
        // recommended pattern).
        boolean verified = false;
        for (Merchant m : merchants.findAll()) {
            if (StripeSignature.verify(sig, raw, m.getWebhookSecret(),
                    Instant.now().getEpochSecond(), props.getWebhook().getMaxAgeSeconds())) {
                verified = true;
                break;
            }
        }
        if (!verified) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid signature"));
        }

        WebhookService.Ingested ingested = svc.ingest(new String(raw, StandardCharsets.UTF_8));
        if (!ingested.accepted()) {
            // Only invalid JSON or missing id/type reach this branch.
            return ResponseEntity.status(400).body(Map.of("error", ingested.error()));
        }
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "duplicate", ingested.duplicate()
        ));
    }
}
