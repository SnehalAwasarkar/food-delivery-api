-- Creates idempotency_records table for short-window idempotency on order placement
-- Unique constraint on idempotency_key prevents concurrent double-tap duplicates.

CREATE TABLE IF NOT EXISTS idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(512) NOT NULL UNIQUE,
    fingerprint VARCHAR(256) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    order_id BIGINT
);
