package com.say5.payflow.service;

import com.say5.payflow.persistence.IdempotencyKey;
import com.say5.payflow.persistence.IdempotencyRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency manager. Callers use {@link #reserveOrReplay} with a
 * per-merchant key + canonicalized request body; the manager guarantees:
 *
 *   - first call with a given (merchant, key) reserves the row and runs
 *     the request handler,
 *   - concurrent calls with the same key while the first is in-flight
 *     get {@link Conflict},
 *   - retries after a finished call replay the stored response,
 *   - retries with a different body raise {@link MismatchedBody},
 *   - if the first call's process crashes before {@link #complete}, the
 *     reservation expires after {@code processingTimeout} and the next
 *     caller reclaims it. Without this fence, a crash would leave the
 *     key permanently 409-Conflicting.
 *
 * Keys live for {@code ttlDays} (configured in application.yml); a
 * cleanup job (DELETE WHERE created_at < now() - interval '7 days') is
 * recommended on a nightly cron.
 */
@Service
public class IdempotencyService {
    private final IdempotencyRepo repo;
    private final Duration processingTimeout;

    public IdempotencyService(IdempotencyRepo repo,
                              @Value("${payflow.idempotency.processing-timeout-seconds:60}")
                              long processingTimeoutSeconds) {
        this.repo = repo;
        this.processingTimeout = Duration.ofSeconds(processingTimeoutSeconds);
    }

    public static class Conflict extends RuntimeException {}
    public static class MismatchedBody extends RuntimeException {}
    public record Result(int status, String body) {}

    /**
     * Reserve the key. Returns an existing completed result (replay) or
     * null to signal the caller should proceed with the real work.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result reserveOrReplay(UUID merchantId, String key, String requestBody) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime deadline = now.plus(processingTimeout);
        String hash = Hashing.sha256Hex(requestBody);

        Optional<IdempotencyKey> existing = repo.findByMerchantIdAndKey(merchantId, key);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (row.isComplete()) {
                if (!row.getRequestHash().equals(hash)) {
                    throw new MismatchedBody();
                }
                return new Result(row.getResponseStatus(), row.getResponseBody());
            }
            // In-flight or abandoned.
            if (row.isAbandoned(now)) {
                row.resetFence(hash, deadline);
                repo.save(row);
                return null;
            }
            if (!row.getRequestHash().equals(hash)) {
                throw new MismatchedBody();
            }
            throw new Conflict();
        }

        try {
            repo.saveAndFlush(new IdempotencyKey(merchantId, key, hash, deadline));
        } catch (DataIntegrityViolationException race) {
            // A concurrent caller inserted first. By construction the
            // freshly-inserted row cannot be abandoned (its fence is
            // > now). Re-fetching inside the same (now rollback-marked)
            // tx is unreliable across JDBC drivers; just signal Conflict.
            // Standard client behavior is to retry with exponential
            // backoff — by then the winner will have either completed
            // (replay) or also crashed (the abandoned-fence path).
            throw new Conflict();
        }
        return null;
    }

    /** Store the final result. Always called outside the request's main
     * transaction so the durable record survives even if the caller
     * rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID merchantId, String key, int status, String body) {
        IdempotencyKey row = repo.findByMerchantIdAndKey(merchantId, key)
            .orElseThrow(() -> new IllegalStateException("key not reserved: " + key));
        row.complete(status, body);
        repo.save(row);
    }
}
