package com.say5.payflow;

import com.say5.payflow.service.Hashing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashingTest {

    @Test
    void sha256HexMatchesKnownVector() {
        // SHA-256("password") known digest.
        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
            Hashing.sha256Hex("password"));
    }

    @Test
    void hmacVsKnownVector() {
        // HMAC-SHA256(key="key", data="The quick brown fox jumps over the lazy dog")
        String expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
        assertEquals(expected,
            Hashing.hmacSha256Hex("key".getBytes(),
                "The quick brown fox jumps over the lazy dog".getBytes()));
    }

    @Test
    void constantTimeEqualsIsLengthSensitive() {
        assertTrue(Hashing.constantTimeEquals("abc", "abc"));
        assertFalse(Hashing.constantTimeEquals("abc", "abcd"));
        assertFalse(Hashing.constantTimeEquals("abc", "abd"));
        assertFalse(Hashing.constantTimeEquals(null, "abc"));
    }
}
