package com.say5.payflow;

import com.say5.payflow.persistence.IdempotencyRepo;
import com.say5.payflow.service.ApiKeyHasher;
import com.say5.payflow.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Idempotency invariants beyond what {@link PaymentFlowIntegrationTest}
 * already covers.
 *
 * Note on the absent "N parallel callers" test: H2's MVCC does not
 * enforce unique-constraint-violation-on-insert with the same
 * synchronous semantics Postgres does, so a literal parallel-storm
 * test running against H2 is unreliable. The real concurrency proof
 * point belongs in the Testcontainers Postgres integration suite —
 * see {@code docs/DEPLOY.md#testing}. The sequential tests below
 * exercise the exact same code paths.
 */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyConcurrencyTest {

    @Autowired IdempotencyService idempotency;
    @Autowired IdempotencyRepo repo;

    static final UUID DEMO = UUID.fromString("00000000-0000-0000-0000-000000000de0");

    @Test
    void secondReservationOfSameKeyAndBodyIsConflict() {
        String key = UUID.randomUUID().toString();
        String body = "{\"amountCents\":1000,\"currency\":\"USD\"}";
        IdempotencyService.Result first = idempotency.reserveOrReplay(DEMO, key, body);
        assertNull(first, "first reservation should proceed (returns null)");
        assertThrows(IdempotencyService.Conflict.class,
            () -> idempotency.reserveOrReplay(DEMO, key, body));
    }

    @Test
    void mismatchedBodyOnInFlightKeyRaisesMismatchedBody() {
        String key = UUID.randomUUID().toString();
        idempotency.reserveOrReplay(DEMO, key, "{\"a\":1}");
        assertThrows(IdempotencyService.MismatchedBody.class,
            () -> idempotency.reserveOrReplay(DEMO, key, "{\"a\":2}"));
    }

    @Test
    void completedKeyReplaysSameBody() {
        String key = UUID.randomUUID().toString();
        String body = "{\"amountCents\":4200,\"currency\":\"USD\"}";
        idempotency.reserveOrReplay(DEMO, key, body);
        idempotency.complete(DEMO, key, 201, "{\"id\":\"pi_x\"}");
        IdempotencyService.Result replay = idempotency.reserveOrReplay(DEMO, key, body);
        assertNotNull(replay);
        assertEquals(201, replay.status());
        assertEquals("{\"id\":\"pi_x\"}", replay.body());
    }

    @Test
    void completedKeyReplayedWithDifferentBodyRaises() {
        String key = UUID.randomUUID().toString();
        idempotency.reserveOrReplay(DEMO, key, "{\"a\":1}");
        idempotency.complete(DEMO, key, 201, "{\"ok\":true}");
        assertThrows(IdempotencyService.MismatchedBody.class,
            () -> idempotency.reserveOrReplay(DEMO, key, "{\"a\":2}"));
    }

    @Test
    void abandonedReservationCanBeReclaimedByLaterCaller() {
        String key = UUID.randomUUID().toString();
        String body = "{\"amountCents\":42,\"currency\":\"USD\"}";

        // First caller reserves, then "crashes" — never calls complete().
        IdempotencyService.Result r1 = idempotency.reserveOrReplay(DEMO, key, body);
        assertNull(r1);

        // Force the row's processing fence into the past (simulates a
        // process that crashed long enough ago for the fence to expire).
        var row = repo.findByMerchantIdAndKey(DEMO, key).orElseThrow();
        row.resetFence(row.getRequestHash(), OffsetDateTime.now().minusMinutes(5));
        repo.saveAndFlush(row);

        // Second caller with same body: should be allowed to retry.
        IdempotencyService.Result r2 = idempotency.reserveOrReplay(DEMO, key, body);
        assertNull(r2, "abandoned key should be reclaimable, not Conflict");
    }

    @Test
    void apiKeyHasherIsActuallySlow() {
        // Sanity check that PBKDF2 isn't accidentally configured with a
        // useless iteration count — defends against silent regressions.
        long t0 = System.nanoTime();
        String hash = ApiKeyHasher.hash("demo-api-key");
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ApiKeyHasher.verify("demo-api-key", hash));
        assertTrue(elapsedMs >= 30,
            "PBKDF2 hash must take at least 30ms; got " + elapsedMs +
            "ms — iteration count too low");
    }
}
