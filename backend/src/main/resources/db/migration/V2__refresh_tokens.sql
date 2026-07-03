-- ============================================================
-- V2: Refresh tokens table for JWT token rotation
-- ============================================================

CREATE TABLE refresh_token (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL REFERENCES users(id),
    token           VARCHAR(500)    NOT NULL UNIQUE,
    expiry_date     TIMESTAMP       NOT NULL,
    is_revoked      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_token ON refresh_token(token);
