ALTER TABLE merchants ADD COLUMN api_key_fingerprint VARCHAR(64);
CREATE INDEX idx_merchants_fingerprint ON merchants(api_key_fingerprint);
DELETE FROM merchants WHERE id = '00000000-0000-0000-0000-000000000de0';
