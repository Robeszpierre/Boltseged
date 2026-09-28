CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_sessions_account ON auth_sessions(account_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions(expires_at);
