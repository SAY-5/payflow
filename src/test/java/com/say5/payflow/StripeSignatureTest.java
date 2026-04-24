package com.say5.payflow;

import com.say5.payflow.webhook.StripeSignature;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StripeSignatureTest {

    private static final String SECRET = "whsec_test_abcdef0123456789";

    @Test
    void verifyAcceptsASelfProducedSignature() {
        byte[] body = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}".getBytes(StandardCharsets.UTF_8);
        long now = 1_700_000_000L;
        String header = StripeSignature.sign(body, SECRET, now);
        assertTrue(StripeSignature.verify(header, body, SECRET, now, 300));
    }

    @Test
    void verifyRejectsTamperedBody() {
        byte[] body = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}".getBytes(StandardCharsets.UTF_8);
        long now = 1_700_000_000L;
        String header = StripeSignature.sign(body, SECRET, now);
        byte[] tampered = "{\"id\":\"evt_1\",\"type\":\"payment_intent.canceled\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(StripeSignature.verify(header, tampered, SECRET, now, 300));
    }

    @Test
    void verifyRejectsOldSignature() {
        byte[] body = "{}".getBytes();
        long then = 1_700_000_000L;
        String header = StripeSignature.sign(body, SECRET, then);
        long now = then + 1_000; // 1000s later > 300s window
        assertFalse(StripeSignature.verify(header, body, SECRET, now, 300));
    }

    @Test
    void verifyRejectsWrongSecret() {
        byte[] body = "{}".getBytes();
        long now = 1_700_000_000L;
        String header = StripeSignature.sign(body, SECRET, now);
        assertFalse(StripeSignature.verify(header, body, "other-secret", now, 300));
    }

    @Test
    void verifyRejectsMalformedHeader() {
        byte[] body = "{}".getBytes();
        assertFalse(StripeSignature.verify(null, body, SECRET, 0, 300));
        assertFalse(StripeSignature.verify("", body, SECRET, 0, 300));
        assertFalse(StripeSignature.verify("bogus=1", body, SECRET, 0, 300));
    }
}
