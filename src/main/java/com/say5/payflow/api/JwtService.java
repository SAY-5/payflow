package com.say5.payflow.api;

import com.say5.payflow.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long ttlMs;

    public JwtService(AppProperties props, Environment env) {
        String raw = props.getAuth().getJwtSecret();
        byte[] secretBytes = raw.getBytes(StandardCharsets.UTF_8);

        boolean isProd = false;
        for (String p : env.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p)) { isProd = true; break; }
        }

        if (secretBytes.length < MIN_SECRET_BYTES) {
            if (isProd) {
                throw new IllegalStateException(
                    "PAYFLOW_JWT_SECRET must be at least " + MIN_SECRET_BYTES +
                    " bytes in the 'prod' profile; got " + secretBytes.length +
                    ". Generate one with `openssl rand -hex 32`.");
            }
            // Dev/test fallback: expand via SHA-256 so the key is the
            // right length, but log loudly so nobody ships this.
            LOG.warn(
                "[security] JWT secret is only {} bytes — expanding via SHA-256 for dev. " +
                "DO NOT use this configuration in production.",
                secretBytes.length);
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
