CREATE TABLE user_storage_usage (
    user_id BIGINT NOT NULL,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    reserved_bytes BIGINT NOT NULL DEFAULT 0,
    file_count BIGINT NOT NULL DEFAULT 0,
    quota_bytes BIGINT NOT NULL DEFAULT 1073741824,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_storage_usage PRIMARY KEY (user_id),
    CONSTRAINT fk_storage_usage_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_storage_usage_numbers CHECK (
        used_bytes >= 0 AND reserved_bytes >= 0 AND file_count >= 0
        AND quota_bytes >= 0 AND used_bytes + reserved_bytes <= quota_bytes
    )
);

INSERT INTO user_storage_usage (user_id)
SELECT id FROM app_user;

CREATE INDEX idx_storage_usage_updated ON user_storage_usage (updated_at, user_id);

CREATE TABLE user_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_name VARCHAR(80) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    declared_mime VARCHAR(127) NOT NULL,
    detected_mime VARCHAR(127) NOT NULL,
    extension VARCHAR(16) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UPLOADING',
    metadata_json TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_file PRIMARY KEY (id),
    CONSTRAINT uk_user_file_public_id UNIQUE (public_id),
    CONSTRAINT uk_user_file_object UNIQUE (storage_provider, object_key),
    CONSTRAINT uk_user_file_id_owner UNIQUE (id, user_id),
    CONSTRAINT fk_user_file_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_file_status CHECK (status IN ('UPLOADING', 'AVAILABLE', 'DELETING', 'FAILED', 'DELETED')),
    CONSTRAINT ck_user_file_size CHECK (size_bytes > 0),
    CONSTRAINT ck_user_file_deleted CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL)
        OR (status <> 'DELETED' AND deleted_at IS NULL)
    )
);

CREATE INDEX idx_user_file_owner_status_created ON user_file (user_id, status, created_at, id);
CREATE INDEX idx_user_file_owner_updated ON user_file (user_id, updated_at, id);
CREATE INDEX idx_user_file_owner_hash_size ON user_file (user_id, sha256, size_bytes);
CREATE INDEX idx_user_file_status_updated ON user_file (status, updated_at, id);

ALTER TABLE chat_message ADD CONSTRAINT uk_chat_message_id_user UNIQUE (id, user_id);

CREATE TABLE chat_message_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    user_file_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    attachment_type VARCHAR(16) NOT NULL DEFAULT 'FILE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_chat_message_attachment PRIMARY KEY (id),
    CONSTRAINT uk_message_attachment UNIQUE (message_id, user_file_id),
    CONSTRAINT fk_attachment_message_owner FOREIGN KEY (message_id, user_id)
        REFERENCES chat_message (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_attachment_file_owner FOREIGN KEY (user_file_id, user_id)
        REFERENCES user_file (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_attachment_type CHECK (attachment_type IN ('FILE', 'IMAGE')),
    CONSTRAINT ck_attachment_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_attachment_message_order ON chat_message_attachment (message_id, sort_order, id);
CREATE INDEX idx_attachment_file_message ON chat_message_attachment (user_file_id, message_id);
CREATE INDEX idx_attachment_owner_created ON chat_message_attachment (user_id, created_at, id);
