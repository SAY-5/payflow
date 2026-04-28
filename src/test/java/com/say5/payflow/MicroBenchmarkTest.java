package com.say5.payflow;

import com.say5.payflow.service.ApiKeyHasher;
import com.say5.payflow.service.Hashing;
import com.say5.payflow.webhook.StripeSignature;
import org.junit.jupiter.api.Test;

/**
 * Lightweight microbenchmarks. Not a JMH harness (kept the dep
 * footprint small) — just timed loops with enough iterations to
 * stabilize the JIT and printed metrics. The asserts only catch
 * catastrophic regressions; the printed numbers are the actual signal
 * for a human reader.
 */
class MicroBenchmarkTest {

    @Test
    void apiKeyHasher_pbkdf2Iterations() {
        // Warmup
        for (int i = 0; i < 5; i++) ApiKeyHasher.hash("warmup-" + i);
        int n = 30;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) ApiKeyHasher.hash("user-" + i);
        long perMs = (System.nanoTime() - t0) / 1_000_000L / n;
        System.out.printf("[bench] ApiKeyHasher.hash    %d ops, %d ms each%n", n, perMs);
        // 30 ms is a soft floor — anything below means PBKDF2 iteration
        // count is too low for a credible auth path.
        org.junit.jupiter.api.Assertions.assertTrue(perMs >= 30,
            "PBKDF2 too fast: " + perMs + "ms — did iteration count regress?");
    }

    @Test
    void stripeSignature_verify_throughput() {
        byte[] body = "{\"id\":\"evt_x\",\"type\":\"payment_intent.succeeded\"}".getBytes();
        String secret = "whsec_test_abcdef0123456789";
        long now = 1_700_000_000L;
        String header = StripeSignature.sign(body, secret, now);
        // Warmup
        for (int i = 0; i < 1000; i++) StripeSignature.verify(header, body, secret, now, 300);
        int n = 50_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) StripeSignature.verify(header, body, secret, now, 300);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        long perMicro = (System.nanoTime() - t0) / 1000 / n;
        System.out.printf("[bench] StripeSignature.verify %d ops in %d ms = ~%d µs/op%n",
            n, elapsedMs, perMicro);
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMs < 5_000,
            "signature verify regression: " + elapsedMs + "ms for " + n);
    }

    @Test
    void hashing_sha256Hex_throughput() {
        // Warmup
        for (int i = 0; i < 1000; i++) Hashing.sha256Hex("warmup-" + i);
        int n = 100_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) Hashing.sha256Hex("payload-" + i);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf("[bench] Hashing.sha256Hex %d ops in %d ms%n", n, elapsedMs);
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMs < 5_000,
            "sha256 regression: " + elapsedMs + "ms for " + n);
    }
}
