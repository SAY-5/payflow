-- Indexable, non-secret fingerprint of the API key so /auth/token can
-- look up merchants in O(1) instead of scanning the table. The actual
-- auth still verifies via PBKDF2 against api_key_hash; the fingerprint
-- is just a prefix of SHA-256(raw_key) and reveals nothing useful to
-- an attacker who reads the table.
ALTER TABLE merchants ADD COLUMN api_key_fingerprint TEXT;
CREATE INDEX IF NOT EXISTS idx_merchants_fingerprint
    ON merchants(api_key_fingerprint);

-- Drop the V2-seeded demo merchant. The app bootstraps it on startup
-- with a freshly-computed PBKDF2 hash so we don't ship a committed
-- hash with a hard-coded salt.
DELETE FROM merchants WHERE id = '00000000-0000-0000-0000-000000000de0';
