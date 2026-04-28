ALTER TABLE idempotency_keys
    ADD COLUMN processing_expires_at TIMESTAMP WITH TIME ZONE;
