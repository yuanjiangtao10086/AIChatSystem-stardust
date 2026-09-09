CREATE TABLE ai_request_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    assistant_message_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    model_code VARCHAR(64) NOT NULL,
    external_model_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    latency_ms BIGINT NULL,
    prompt_tokens BIGINT NULL,
    completion_tokens BIGINT NULL,
    total_tokens BIGINT NULL,
    error_code VARCHAR(64) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_request_log PRIMARY KEY (id),
    CONSTRAINT uk_ai_request_log_request UNIQUE (request_id),
    CONSTRAINT uk_ai_request_log_message UNIQUE (assistant_message_id),
    CONSTRAINT fk_ai_request_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_request_log_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_request_log_message FOREIGN KEY (assistant_message_id) REFERENCES chat_message (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_request_log_provider FOREIGN KEY (provider_id) REFERENCES ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_request_log_model FOREIGN KEY (model_id) REFERENCES ai_model (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_request_log_status CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'STOPPED', 'FAILED')),
    CONSTRAINT ck_ai_request_log_numbers CHECK (
        (latency_ms IS NULL OR latency_ms >= 0)
        AND (prompt_tokens IS NULL OR prompt_tokens >= 0)
        AND (completion_tokens IS NULL OR completion_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
    )
);

CREATE INDEX idx_ai_request_user_created ON ai_request_log (user_id, created_at, id);
CREATE INDEX idx_ai_request_conversation_created ON ai_request_log (conversation_id, created_at, id);
CREATE INDEX idx_ai_request_status_created ON ai_request_log (status, created_at, id);
CREATE INDEX idx_ai_request_provider_model_created ON ai_request_log (provider_id, model_id, created_at, id);
CREATE INDEX idx_ai_request_updated ON ai_request_log (updated_at, id);
