package com.say5.payflow.api;

import com.say5.payflow.persistence.Merchant;
import com.say5.payflow.persistence.MerchantRepo;
import com.say5.payflow.service.Hashing;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Exchange a raw API key for a short-lived JWT. */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final MerchantRepo merchants;
    private final JwtService jwt;

    public AuthController(MerchantRepo merchants, JwtService jwt) {
        this.merchants = merchants;
        this.jwt = jwt;
    }

    public record TokenRequest(String apiKey) {}

    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest body) {
        if (body == null || body.apiKey == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiKey required"));
        }
        // API keys are stored as "sha256:<merchant-short>:<hex>". We match
        // on the full hash portion only.
        String candidate = "sha256:" + Hashing.sha256Hex(body.apiKey);
        for (Merchant m : merchants.findAll()) {
            String stored = m.getApiKeyHash();
            // Support seeded dev merchant whose stored hash is already
            // sha256-hex — allow matching either raw sha256-hex or the
            // labeled form.
            String storedHash = stored.startsWith("sha256:") ? stored.split(":")[stored.split(":").length - 1] : stored;
            String candHash = candidate.split(":")[candidate.split(":").length - 1];
            if (Hashing.constantTimeEquals(storedHash, candHash)) {
                return ResponseEntity.ok(Map.of(
                    "token", jwt.issue(m.getId()),
                    "merchantId", m.getId().toString(),
                    "expiresInSeconds", 60 * 60
                ));
            }
        }
        return ResponseEntity.status(401).body(Map.of("error", "invalid api key"));
    }
}
