package com.say5.payflow.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * API key hashing using PBKDF2-HMAC-SHA256 with per-key random salt.
 *
 * Rationale: SHA-256 is too fast — an attacker with a stolen
 * api_key_hash table can brute-force all keys in seconds. We use
 * PBKDF2 instead because it's in the JDK (no extra dep), with 210k
 * iterations (OWASP 2024 recommendation) and a 16-byte salt.
 *
 * Storage format: "pbkdf2:sha256:<iter>:<base64-salt>:<base64-hash>".
 * For backward compatibility with the seeded demo merchant we also
 * accept the legacy "sha256:<label>:<hex>" form, but new merchants
 * always use the PBKDF2 form.
 */
public final class ApiKeyHasher {
    public static final int ITERATIONS = 210_000;
    public static final int KEY_LENGTH_BITS = 256;
    public static final int SALT_LENGTH_BYTES = 16;

    private static final SecureRandom RNG = new SecureRandom();

    private ApiKeyHasher() {}

    public static String hash(String rawKey) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RNG.nextBytes(salt);
        byte[] dk = pbkdf2(rawKey, salt, ITERATIONS);
        return "pbkdf2:sha256:" + ITERATIONS + ":" +
            Base64.getEncoder().encodeToString(salt) + ":" +
            Base64.getEncoder().encodeToString(dk);
    }

    public static boolean verify(String rawKey, String stored) {
        if (stored == null || rawKey == null) return false;
        if (stored.startsWith("pbkdf2:sha256:")) {
            String[] parts = stored.split(":");
            if (parts.length != 5) return false;
            int iters;
            byte[] salt, expected;
            try {
                iters = Integer.parseInt(parts[2]);
                salt = Base64.getDecoder().decode(parts[3]);
                expected = Base64.getDecoder().decode(parts[4]);
            } catch (Exception e) {
                return false;
            }
            byte[] candidate = pbkdf2(rawKey, salt, iters);
            return Hashing.constantTimeEqualsBytes(candidate, expected);
        }
        // Legacy "sha256:<label>:<hex>" — accepted but flagged for migration.
        if (stored.startsWith("sha256:")) {
            String[] parts = stored.split(":");
            if (parts.length < 2) return false;
            String hex = parts[parts.length - 1];
            String candidate = Hashing.sha256Hex(rawKey);
            return Hashing.constantTimeEquals(candidate, hex);
        }
        return false;
    }

    private static byte[] pbkdf2(String pwd, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(pwd.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        }
    }

    /** Helper for the auth controller's "find merchant by raw key" step. */
    public static String fingerprint(String rawKey) {
        // A non-secret-quality fingerprint we can index for quick lookup.
        // SHA-256 of the raw key truncated to 12 bytes — collisions are
        // a non-issue at any realistic scale; the actual auth check
        // still uses PBKDF2 verify().
        return Hashing.sha256Hex(rawKey).substring(0, 24);
    }
}
