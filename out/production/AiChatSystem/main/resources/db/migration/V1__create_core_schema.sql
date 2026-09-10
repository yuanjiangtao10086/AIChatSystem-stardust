CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    username VARCHAR(64) NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ban_reason VARCHAR(500) NULL,
    banned_until DATETIME(3) NULL,
    password_changed_at DATETIME(3) NULL,
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_public_id UNIQUE (public_id),
    CONSTRAINT uk_app_user_email UNIQUE (email_normalized),
    CONSTRAINT ck_app_user_status CHECK (status IN ('NORMAL', 'BANNED', 'DISABLED', 'DELETED')),
    CONSTRAINT ck_app_user_deleted CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL)
        OR (status <> 'DELETED' AND deleted_at IS NULL)
    )
);

CREATE INDEX idx_app_user_status_created ON app_user (status, created_at, id);
CREATE INDEX idx_app_user_updated ON app_user (updated_at, id);

CREATE TABLE app_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_role PRIMARY KEY (id),
    CONSTRAINT uk_app_role_code UNIQUE (code),
    CONSTRAINT ck_app_role_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE INDEX idx_app_role_status_created ON app_role (status, created_at, id);

CREATE TABLE app_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_user_role PRIMARY KEY (id),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_role_grantor FOREIGN KEY (granted_by) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE INDEX idx_user_role_role_user ON app_user_role (role_id, user_id);
CREATE INDEX idx_user_role_created ON app_user_role (created_at, id);

CREATE TABLE ai_provider (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    credential_ref VARCHAR(255) NULL,
    non_secret_config_json TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    health_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    last_health_checked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_provider PRIMARY KEY (id),
    CONSTRAINT uk_ai_provider_public_id UNIQUE (public_id),
    CONSTRAINT uk_ai_provider_code UNIQUE (code),
    CONSTRAINT ck_ai_provider_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_provider_health CHECK (health_status IN ('UNKNOWN', 'HEALTHY', 'DEGRADED', 'UNAVAILABLE'))
);

CREATE INDEX idx_ai_provider_status_type ON ai_provider (status, provider_type);
CREATE INDEX idx_ai_provider_updated ON ai_provider (updated_at, id);

CREATE TABLE ai_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    provider_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    external_model_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(24) NOT NULL,
    capabilities_json TEXT NOT NULL,
    context_window INTEGER NULL,
    max_output_tokens INTEGER NULL,
    input_price DECIMAL(19, 8) NULL,
    output_price DECIMAL(19, 8) NULL,
    currency CHAR(3) NULL,
    price_effective_from DATETIME(3) NULL,
    parameter_policy_json TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_model PRIMARY KEY (id),
    CONSTRAINT uk_ai_model_public_id UNIQUE (public_id),
    CONSTRAINT uk_ai_model_code UNIQUE (code),
    CONSTRAINT uk_ai_model_provider_external UNIQUE (provider_id, external_model_id),
    CONSTRAINT fk_ai_model_provider FOREIGN KEY (provider_id) REFERENCES ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_model_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_model_type CHECK (model_type IN ('CHAT', 'EMBEDDING', 'RERANK', 'MULTIMODAL')),
    CONSTRAINT ck_ai_model_token_limits CHECK (
        (context_window IS NULL OR context_window > 0)
        AND (max_output_tokens IS NULL OR max_output_tokens > 0)
    ),
    CONSTRAINT ck_ai_model_prices CHECK (
        (input_price IS NULL OR input_price >= 0)
        AND (output_price IS NULL OR output_price >= 0)
    )
);

CREATE INDEX idx_ai_model_status_type_sort ON ai_model (status, model_type, sort_order);
CREATE INDEX idx_ai_model_provider_status ON ai_model (provider_id, status, sort_order);
CREATE INDEX idx_ai_model_updated ON ai_model (updated_at, id);

CREATE TABLE conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    default_model_id BIGINT NULL,
    last_message_at DATETIME(3) NULL,
    message_count BIGINT NOT NULL DEFAULT 0,
    next_sequence_no BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_conversation PRIMARY KEY (id),
    CONSTRAINT uk_conversation_public_id UNIQUE (public_id),
    CONSTRAINT uk_conversation_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_conversation_default_model FOREIGN KEY (default_model_id) REFERENCES ai_model (id) ON DELETE SET NULL,
    CONSTRAINT ck_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    CONSTRAINT ck_conversation_deleted CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL)
        OR (status <> 'DELETED' AND deleted_at IS NULL)
    ),
    CONSTRAINT ck_conversation_counts CHECK (message_count >= 0 AND next_sequence_no >= 1)
);

CREATE INDEX idx_conversation_user_status_last ON conversation (user_id, status, last_message_at, id);
CREATE INDEX idx_conversation_user_updated ON conversation (user_id, updated_at, id);

CREATE TABLE chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    sequence_no BIGINT NOT NULL,
    parent_message_id BIGINT NULL,
    supersedes_message_id BIGINT NULL,
    variant_no INTEGER NOT NULL DEFAULT 0,
    content_text LONGTEXT NOT NULL,
    content_format VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    finish_reason VARCHAR(24) NULL,
    error_code VARCHAR(64) NULL,
    model_id BIGINT NULL,
    prompt_tokens BIGINT NULL,
    completion_tokens BIGINT NULL,
    checkpoint_seq BIGINT NULL,
    checkpoint_at DATETIME(3) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    client_request_id VARCHAR(64) NULL,
    content_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_message PRIMARY KEY (id),
    CONSTRAINT uk_chat_message_public_id UNIQUE (public_id),
    CONSTRAINT uk_chat_message_id_owner UNIQUE (id, conversation_id, user_id),
    CONSTRAINT uk_message_sequence_variant UNIQUE (conversation_id, sequence_no, variant_no),
    CONSTRAINT uk_message_user_client_request UNIQUE (user_id, client_request_id),
    CONSTRAINT fk_message_conversation_owner FOREIGN KEY (conversation_id, user_id)
        REFERENCES conversation (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_message_parent_owner FOREIGN KEY (parent_message_id, conversation_id, user_id)
        REFERENCES chat_message (id, conversation_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_message_supersedes_owner FOREIGN KEY (supersedes_message_id, conversation_id, user_id)
        REFERENCES chat_message (id, conversation_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_message_model FOREIGN KEY (model_id) REFERENCES ai_model (id) ON DELETE SET NULL,
    CONSTRAINT ck_chat_message_role CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
    CONSTRAINT ck_chat_message_format CHECK (content_format IN ('PLAIN_TEXT', 'MARKDOWN', 'JSON')),
    CONSTRAINT ck_chat_message_status CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'STOPPED', 'FAILED', 'DELETED')),
    CONSTRAINT ck_chat_message_finish CHECK (
        finish_reason IS NULL OR finish_reason IN ('STOP', 'LENGTH', 'USER_CANCELLED', 'CONTENT_FILTER', 'TOOL_CALL', 'ERROR')
    ),
    CONSTRAINT ck_chat_message_deleted CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL)
        OR (status <> 'DELETED' AND deleted_at IS NULL)
    ),
    CONSTRAINT ck_chat_message_numbers CHECK (
        sequence_no >= 1
        AND variant_no >= 0
        AND (prompt_tokens IS NULL OR prompt_tokens >= 0)
        AND (completion_tokens IS NULL OR completion_tokens >= 0)
        AND (checkpoint_seq IS NULL OR checkpoint_seq >= 0)
    )
);

CREATE INDEX idx_message_conversation_sequence ON chat_message (conversation_id, sequence_no, id);
CREATE INDEX idx_message_user_status_created ON chat_message (user_id, status, created_at, id);
CREATE INDEX idx_message_parent_variant ON chat_message (parent_message_id, variant_no);
CREATE INDEX idx_message_updated ON chat_message (updated_at, id);
