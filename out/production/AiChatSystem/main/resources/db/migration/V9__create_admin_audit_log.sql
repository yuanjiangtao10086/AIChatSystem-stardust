CREATE TABLE admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    admin_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_user_id BIGINT NULL,
    target_resource_type VARCHAR(64) NOT NULL,
    target_resource_id VARCHAR(64) NULL,
    ip VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500) NULL,
    request_id VARCHAR(64) NOT NULL,
    metadata_json TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id),
    CONSTRAINT uk_admin_audit_public_id UNIQUE (public_id),
    CONSTRAINT fk_admin_audit_admin FOREIGN KEY (admin_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admin_audit_target_user FOREIGN KEY (target_user_id) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE INDEX idx_admin_audit_admin_created ON admin_audit_log (admin_id, created_at, id);
CREATE INDEX idx_admin_audit_target_created ON admin_audit_log (target_resource_type, target_resource_id, created_at, id);
CREATE INDEX idx_admin_audit_user_created ON admin_audit_log (target_user_id, created_at, id);
CREATE INDEX idx_admin_audit_action_created ON admin_audit_log (action, created_at, id);
CREATE INDEX idx_admin_audit_request ON admin_audit_log (request_id);
