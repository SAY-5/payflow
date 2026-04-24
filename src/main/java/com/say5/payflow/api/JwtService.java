package com.say5.payflow.api;

import com.say5.payflow.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMs;

    public JwtService(AppProperties props) {
        byte[] secretBytes = props.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        // HMAC-SHA256 requires >= 256 bits. Expand short dev secrets via a
        // SHA-256 hash so we always have 32 bytes regardless of config.
        if (secretBytes.length < 32) {
            try {
                secretBytes = MessageDigest.getInstance("SHA-256").digest(secretBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttlMs = props.getAuth().getJwtTtlMinutes() * 60_000L;
    }

    public String issue(UUID merchantId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(merchantId.toString())
            .issuedAt(new Date(now))
            .expiration(new Date(now + ttlMs))
            .signWith(key)
            .compact();
    }

    public UUID verify(String token) {
        Jws<Claims> parsed = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return UUID.fromString(parsed.getPayload().getSubject());
    }
}
