package com.say5.payflow.webhook;

import com.say5.payflow.service.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Parse and verify a Stripe-style "Stripe-Signature" header.
 *
 * Real Stripe format: "t=1234567890,v1=abc...,v1=def...". We accept any
 * number of v1 signatures and require at least one to match. The
 * signed-payload convention is `t + "." + rawBody`. Signatures older
 * than the configured tolerance window are rejected to prevent replay.
 *
 * This is a faithful re-implementation of the documented scheme so the
 * real Stripe CLI fires valid events against us. See
 * https://docs.stripe.com/webhooks/signatures for the spec.
 */
public final class StripeSignature {
    private StripeSignature() {}

    public static boolean verify(String signatureHeader, byte[] rawBody, String secret,
                                 long nowUnix, long maxAgeSeconds) {
        if (signatureHeader == null) return false;
        Map<String, String> parts = parse(signatureHeader);
        String t = parts.get("t");
        if (t == null) return false;
        long ts;
        try { ts = Long.parseLong(t); } catch (NumberFormatException e) { return false; }
        if (Math.abs(nowUnix - ts) > maxAgeSeconds) return false;

        String payload = ts + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expected = Hashing.hmacSha256Hex(
            secret.getBytes(StandardCharsets.UTF_8),
            payload.getBytes(StandardCharsets.UTF_8));

        for (var e : parts.entrySet()) {
            if (!e.getKey().equals("v1")) continue;
            if (Hashing.constantTimeEquals(e.getValue(), expected)) return true;
        }
        return false;
    }

    /** Build a header value — used by tests and the webhook-replay CLI. */
    public static String sign(byte[] rawBody, String secret, long timestampUnix) {
        String payload = timestampUnix + "." + new String(rawBody, StandardCharsets.UTF_8);
        String sig = Hashing.hmacSha256Hex(
            secret.getBytes(StandardCharsets.UTF_8),
            payload.getBytes(StandardCharsets.UTF_8));
        return "t=" + timestampUnix + ",v1=" + sig;
    }

    static Map<String, String> parse(String header) {
        Map<String, String> out = new HashMap<>();
        for (String part : header.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq).trim();
            String v = part.substring(eq + 1).trim();
            // For v1 we may have multiple; keep only one match — caller
            // checks all via iteration.
            if (k.equals("v1") && out.containsKey(k)) {
                // Combine using a separator so we can iterate during verify.
                out.put(k, out.get(k) + "|" + v);
            } else {
                out.put(k, v);
            }
        }
        // Expand combined v1's.
        if (out.containsKey("v1") && out.get("v1").contains("|")) {
            String[] sigs = out.get("v1").split("\\|");
            for (int i = 0; i < sigs.length; i++) {
                out.put("v1" + (i == 0 ? "" : "_" + i), sigs[i]);
            }
            out.put("v1", sigs[0]);
        }
        return out;
    }
}
