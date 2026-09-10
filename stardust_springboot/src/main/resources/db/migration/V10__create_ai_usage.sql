CREATE TABLE ai_usage_account (
    user_id BIGINT NOT NULL,
    quota_tokens BIGINT NOT NULL,
    used_tokens BIGINT NOT NULL DEFAULT 0,
    reserved_tokens BIGINT NOT NULL DEFAULT 0,
    quota_cost DECIMAL(19,8) NOT NULL,
    used_cost DECIMAL(19,8) NOT NULL DEFAULT 0,
    reserved_cost DECIMAL(19,8) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    period_start DATETIME(3) NOT NULL,
    period_end DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_usage_account PRIMARY KEY (user_id),
    CONSTRAINT fk_ai_usage_account_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_usage_account_numbers CHECK (
        quota_tokens >= 0 AND used_tokens >= 0 AND reserved_tokens >= 0
        AND quota_cost >= 0 AND used_cost >= 0 AND reserved_cost >= 0
    ),
    CONSTRAINT ck_ai_usage_account_quota CHECK (
        used_tokens + reserved_tokens <= quota_tokens
        AND used_cost + reserved_cost <= quota_cost
    ),
    CONSTRAINT ck_ai_usage_account_period CHECK (period_end > period_start)
);

INSERT INTO ai_usage_account (user_id, quota_tokens, quota_cost, currency, period_start, period_end)
SELECT id, 2000000, 20.00000000, 'USD', CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, 30, CURRENT_TIMESTAMP)
FROM app_user;

CREATE INDEX idx_ai_usage_account_period ON ai_usage_account (period_end, user_id);

CREATE TABLE ai_usage_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    ai_request_id CHAR(26) NULL,
    operation_key VARCHAR(80) NOT NULL,
    entry_type VARCHAR(16) NOT NULL,
    token_delta BIGINT NOT NULL,
    cost_delta DECIMAL(19,8) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_usage_ledger PRIMARY KEY (id),
    CONSTRAINT uk_ai_usage_ledger_public_id UNIQUE (public_id),
    CONSTRAINT uk_ai_usage_ledger_operation UNIQUE (ai_request_id, operation_key),
    CONSTRAINT fk_ai_usage_ledger_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_usage_ledger_type CHECK (entry_type IN ('RESERVE', 'SETTLE', 'RELEASE', 'ADJUST')),
    CONSTRAINT ck_ai_usage_ledger_operation CHECK (operation_key <> '')
);

CREATE INDEX idx_ai_usage_ledger_user_created ON ai_usage_ledger (user_id, created_at, id);
CREATE INDEX idx_ai_usage_ledger_request ON ai_usage_ledger (ai_request_id);
