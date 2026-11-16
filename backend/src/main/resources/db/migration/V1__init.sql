-- V1: core schema for the MVP (users, wallets, transactions, ledger, refresh tokens).
-- payment_orders, outbox_events and audit_logs are added in later migrations.
-- Design notes: docs/database.md

CREATE TABLE users (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(254) NOT NULL UNIQUE,
    phone               VARCHAR(15)  NOT NULL UNIQUE,
    full_name           VARCHAR(100) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    pin_hash            VARCHAR(100),
    pin_failed_attempts SMALLINT     NOT NULL DEFAULT 0,
    pin_locked_until    TIMESTAMPTZ,
    role                VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        UNIQUE REFERENCES users (id),
    type       VARCHAR(20) NOT NULL CHECK (type IN ('USER', 'SYSTEM')),
    code       VARCHAR(30) UNIQUE,
    currency   CHAR(3)     NOT NULL DEFAULT 'VND',
    balance    BIGINT      NOT NULL DEFAULT 0,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED')),
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Customer balances can never go negative (BR-02). The system wallet can (ADR 0005).
    CONSTRAINT chk_wallets_balance_non_negative CHECK (type = 'SYSTEM' OR balance >= 0),
    CONSTRAINT chk_wallets_owner CHECK (
        (type = 'USER' AND user_id IS NOT NULL) OR (type = 'SYSTEM' AND code IS NOT NULL)
    )
);

CREATE TABLE transactions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_code  VARCHAR(20) NOT NULL UNIQUE,
    type            VARCHAR(20) NOT NULL CHECK (type IN ('TOPUP', 'TRANSFER')),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    amount          BIGINT      NOT NULL CHECK (amount > 0),
    from_wallet_id  UUID        NOT NULL REFERENCES wallets (id),
    to_wallet_id    UUID        NOT NULL REFERENCES wallets (id),
    created_by      UUID        NOT NULL REFERENCES users (id),
    idempotency_key UUID        NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    note            VARCHAR(100),
    failure_reason  VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    -- Second line of defence for idempotency, behind Redis.
    CONSTRAINT uq_transactions_idempotency UNIQUE (created_by, idempotency_key),
    CONSTRAINT chk_transactions_distinct_wallets CHECK (from_wallet_id <> to_wallet_id)
);

CREATE INDEX idx_transactions_from_wallet ON transactions (from_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_to_wallet ON transactions (to_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_pending ON transactions (status, created_at) WHERE status = 'PENDING';

-- Insert-only. Every successful transaction writes entries that sum to zero.
CREATE TABLE ledger_entries (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id UUID        NOT NULL REFERENCES transactions (id),
    wallet_id      UUID        NOT NULL REFERENCES wallets (id),
    amount         BIGINT      NOT NULL CHECK (amount <> 0),
    balance_after  BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_entries_transaction_wallet UNIQUE (transaction_id, wallet_id)
);

CREATE INDEX idx_ledger_entries_wallet ON ledger_entries (wallet_id, id);

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- System wallet that funds top-ups (ADR 0005).
INSERT INTO wallets (type, code) VALUES ('SYSTEM', 'SYSTEM_TOPUP');
