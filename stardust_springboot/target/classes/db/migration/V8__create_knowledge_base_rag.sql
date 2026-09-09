CREATE TABLE knowledge_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_knowledge_base PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_public UNIQUE (public_id),
    CONSTRAINT uk_knowledge_base_id_owner UNIQUE (id, user_id),
    CONSTRAINT fk_knowledge_base_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_knowledge_base_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_knowledge_base_deleted CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL)
        OR (status = 'ACTIVE' AND deleted_at IS NULL)
    )
);

CREATE INDEX idx_kb_owner_status_updated ON knowledge_base (user_id, status, updated_at, id);

CREATE TABLE knowledge_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_file_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED',
    parser_type VARCHAR(32) NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    processing_version INTEGER NOT NULL DEFAULT 1,
    embedding_provider VARCHAR(64) NULL,
    embedding_model VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_knowledge_document PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_document_public UNIQUE (public_id),
    CONSTRAINT uk_knowledge_document_id_owner UNIQUE (id, knowledge_base_id, user_id),
    CONSTRAINT uk_knowledge_document_file UNIQUE (knowledge_base_id, user_file_id),
    CONSTRAINT fk_knowledge_document_kb_owner FOREIGN KEY (knowledge_base_id, user_id)
        REFERENCES knowledge_base (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_knowledge_document_file_owner FOREIGN KEY (user_file_id, user_id)
        REFERENCES user_file (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_knowledge_document_status CHECK (
        status IN ('UPLOADED','PARSING','PARSED','EMBEDDING','READY','FAILED')
    ),
    CONSTRAINT ck_knowledge_document_counts CHECK (chunk_count >= 0 AND processing_version >= 1)
);

CREATE INDEX idx_document_kb_status_created
    ON knowledge_document (knowledge_base_id, status, created_at, id);
CREATE INDEX idx_document_owner_status_updated
    ON knowledge_document (user_id, status, updated_at, id);
CREATE INDEX idx_document_file_owner ON knowledge_document (user_file_id, user_id, deleted_at);

CREATE TABLE document_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    knowledge_document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content_text TEXT NOT NULL,
    token_count INTEGER NOT NULL,
    page_number INTEGER NULL,
    source_metadata_json TEXT NULL,
    content_hash CHAR(64) NOT NULL,
    processing_version INTEGER NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_document_chunk PRIMARY KEY (id),
    CONSTRAINT uk_document_chunk_public UNIQUE (public_id),
    CONSTRAINT uk_document_chunk_version UNIQUE (
        knowledge_document_id, processing_version, chunk_index
    ),
    CONSTRAINT fk_document_chunk_document_owner
        FOREIGN KEY (knowledge_document_id, knowledge_base_id, user_id)
        REFERENCES knowledge_document (id, knowledge_base_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_chunk_numbers CHECK (
        chunk_index >= 0 AND token_count > 0 AND processing_version >= 1
        AND (page_number IS NULL OR page_number >= 1)
    )
);

CREATE INDEX idx_chunk_kb_owner ON document_chunk (knowledge_base_id, user_id, id);
CREATE INDEX idx_chunk_document_order ON document_chunk (knowledge_document_id, processing_version, chunk_index);

CREATE TABLE conversation_knowledge_base (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_conversation_knowledge_base PRIMARY KEY (id),
    CONSTRAINT uk_conversation_knowledge_base UNIQUE (conversation_id, knowledge_base_id),
    CONSTRAINT fk_conversation_kb_conversation_owner FOREIGN KEY (conversation_id, user_id)
        REFERENCES conversation (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_conversation_kb_knowledge_owner FOREIGN KEY (knowledge_base_id, user_id)
        REFERENCES knowledge_base (id, user_id) ON DELETE RESTRICT
);

CREATE INDEX idx_conversation_kb_owner ON conversation_knowledge_base (user_id, conversation_id);
CREATE INDEX idx_kb_conversations ON conversation_knowledge_base (knowledge_base_id, conversation_id);
