-- Add a processing fence so a key reserved by a process that crashed
-- before completion can be reclaimed by the next caller after the fence
-- expires. Without this, a crash leaves the (merchant, key) pair
-- permanently in 409-Conflict state.
ALTER TABLE idempotency_keys
    ADD COLUMN processing_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_idempotency_processing_fence
    ON idempotency_keys(processing_expires_at)
    WHERE completed_at IS NULL;
