CREATE TABLE conversation_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    summary_version BIGINT NOT NULL,
    summary_text TEXT NOT NULL,
    covered_through_message_id BIGINT NOT NULL,
    covered_through_sequence_no BIGINT NOT NULL,
    source_message_count INTEGER NOT NULL,
    estimated_tokens INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_conversation_summary PRIMARY KEY (id),
    CONSTRAINT uk_conversation_summary_public_id UNIQUE (public_id),
    CONSTRAINT uk_conversation_summary_version UNIQUE (conversation_id, summary_version),
    CONSTRAINT uk_conversation_summary_anchor UNIQUE (conversation_id, covered_through_message_id),
    CONSTRAINT fk_summary_conversation_owner FOREIGN KEY (conversation_id, user_id)
        REFERENCES conversation (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_summary_message_owner FOREIGN KEY (covered_through_message_id, conversation_id, user_id)
        REFERENCES chat_message (id, conversation_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_conversation_summary_status CHECK (status IN ('ACTIVE', 'INVALID')),
    CONSTRAINT ck_conversation_summary_counts CHECK (
        summary_version > 0 AND covered_through_sequence_no > 0
        AND source_message_count > 0 AND estimated_tokens > 0
    )
);

CREATE INDEX idx_summary_conversation_status_version
    ON conversation_summary (conversation_id, status, summary_version, id);
CREATE INDEX idx_summary_owner_updated
    ON conversation_summary (user_id, updated_at, id);
CREATE INDEX idx_summary_anchor_status
    ON conversation_summary (covered_through_message_id, status, id);
