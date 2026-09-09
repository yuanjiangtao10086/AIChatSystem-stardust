CREATE TABLE user_memory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    content_text TEXT NOT NULL,
    summary VARCHAR(300) NOT NULL,
    memory_type VARCHAR(24) NOT NULL,
    importance INTEGER NOT NULL,
    source_conversation_id BIGINT NULL,
    source_message_id BIGINT NULL,
    origin VARCHAR(16) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_memory PRIMARY KEY (id),
    CONSTRAINT uk_user_memory_public_id UNIQUE (public_id),
    CONSTRAINT fk_user_memory_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_memory_conversation_owner FOREIGN KEY (source_conversation_id, user_id)
        REFERENCES conversation (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_memory_message_owner FOREIGN KEY (source_message_id, source_conversation_id, user_id)
        REFERENCES chat_message (id, conversation_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_memory_type CHECK (memory_type IN ('PREFERENCE','PROJECT','GOAL','EXPLICIT')),
    CONSTRAINT ck_user_memory_origin CHECK (origin IN ('MANUAL','AUTO')),
    CONSTRAINT ck_user_memory_importance CHECK (importance BETWEEN 1 AND 100),
    CONSTRAINT ck_user_memory_source CHECK (
        (source_message_id IS NULL AND source_conversation_id IS NULL)
        OR (source_message_id IS NOT NULL AND source_conversation_id IS NOT NULL)
    ),
    CONSTRAINT ck_user_memory_deleted CHECK (
        (deleted_at IS NULL) OR (deleted_at IS NOT NULL AND enabled = FALSE)
    )
);

CREATE INDEX idx_memory_owner_enabled_importance
    ON user_memory (user_id, enabled, deleted_at, importance, updated_at, id);
CREATE INDEX idx_memory_owner_type_updated
    ON user_memory (user_id, memory_type, updated_at, id);
CREATE INDEX idx_memory_owner_hash
    ON user_memory (user_id, content_hash, deleted_at, id);
CREATE INDEX idx_memory_source_conversation
    ON user_memory (source_conversation_id, created_at, id);
CREATE INDEX idx_memory_source_message ON user_memory (source_message_id, id);
