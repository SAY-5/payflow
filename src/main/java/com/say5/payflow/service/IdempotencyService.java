package com.say5.payflow.service;

import com.say5.payflow.persistence.IdempotencyKey;
import com.say5.payflow.persistence.IdempotencyRepo;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency manager. Callers use {@link #execute} with a per-merchant
 * key + canonicalized request body; the manager guarantees:
 *
 *   - the first call with a given (merchant, key) runs the supplier and
 *     stores its result,
 *   - concurrent calls with the same key either return the stored result
 *     (if finished) or raise {@link Conflict} (if still in flight),
 *   - reusing a key with a different body raises {@link MismatchedBody}.
 *
 * Keys live for ttlDays (configured in application.yml); a simple nightly
 * cleanup job (not shipped here; recommended DELETE WHERE created_at <
 * now() - interval '7 days') is enough.
 */
@Service
public class IdempotencyService {
    private final IdempotencyRepo repo;
    private final EntityManager em;

    public IdempotencyService(IdempotencyRepo repo, EntityManager em) {
        this.repo = repo;
        this.em = em;
    }

    public static class Conflict extends RuntimeException {}
    public static class MismatchedBody extends RuntimeException {}
    public record Result(int status, String body) {}

    /**
     * Reserve the key. Called in its own transaction so the insert is
     * immediately visible to concurrent callers. Returns an existing
     * completed result (triggering a replay) or null to signal the caller
     * should proceed with the real work.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result reserveOrReplay(UUID merchantId, String key, String requestBody) {
        String hash = Hashing.sha256Hex(requestBody);
        Optional<IdempotencyKey> existing = repo.findByMerchantIdAndKey(merchantId, key);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (!row.getRequestHash().equals(hash)) {
                throw new MismatchedBody();
            }
            if (!row.isComplete()) {
                throw new Conflict();
            }
            return new Result(row.getResponseStatus(), row.getResponseBody());
        }
        try {
            repo.saveAndFlush(new IdempotencyKey(merchantId, key, hash));
        } catch (DataIntegrityViolationException race) {
            // Another request inserted first. Re-fetch and re-resolve.
            IdempotencyKey row = repo.findByMerchantIdAndKey(merchantId, key)
                .orElseThrow(() -> race);
            if (!row.getRequestHash().equals(hash)) throw new MismatchedBody();
            if (!row.isComplete()) throw new Conflict();
            return new Result(row.getResponseStatus(), row.getResponseBody());
        }
        return null;
    }

    /** Store the final result for the key. Called after the caller's work
     * completes successfully, in its own transaction so it's durable
     * regardless of the outer tx state. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID merchantId, String key, int status, String body) {
        IdempotencyKey row = repo.findByMerchantIdAndKey(merchantId, key)
            .orElseThrow(() -> new IllegalStateException("key not reserved: " + key));
        row.complete(status, body);
        repo.save(row);
    }
}
