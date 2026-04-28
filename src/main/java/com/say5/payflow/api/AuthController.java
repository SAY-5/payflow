package com.say5.payflow.api;

import com.say5.payflow.persistence.Merchant;
import com.say5.payflow.persistence.MerchantRepo;
import com.say5.payflow.service.ApiKeyHasher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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

    public record TokenRequest(
        @NotBlank @Size(min = 8, max = 256) String apiKey
    ) {}

    @PostMapping("/token")
    public ResponseEntity<?> token(@Valid @RequestBody TokenRequest body) {
        // Narrow by a non-secret fingerprint (prefix of SHA-256 of the
        // raw key) so we don't scan the full merchant table and run a
        // real PBKDF2 verification N times per login.
        String fp = ApiKeyHasher.fingerprint(body.apiKey());
        Optional<Merchant> match = merchants.findByApiKeyFingerprint(fp);
        if (match.isPresent() && ApiKeyHasher.verify(body.apiKey(), match.get().getApiKeyHash())) {
            Merchant m = match.get();
            return ResponseEntity.ok(Map.of(
                "token", jwt.issue(m.getId()),
                "merchantId", m.getId().toString(),
                "expiresInSeconds", 60 * 60
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "invalid api key"));
    }
}
