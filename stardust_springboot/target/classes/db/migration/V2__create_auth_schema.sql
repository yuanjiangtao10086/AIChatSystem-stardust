ALTER TABLE app_user
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(26) NOT NULL,
    user_id BIGINT NOT NULL,
    family_id CHAR(26) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    rotated_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    replaced_by_id BIGINT NULL,
    last_used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_public_id UNIQUE (public_id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_refresh_token_id_user_family UNIQUE (id, user_id, family_id),
    CONSTRAINT uk_refresh_token_replaced_by UNIQUE (replaced_by_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_token_replacement FOREIGN KEY (replaced_by_id, user_id, family_id)
        REFERENCES refresh_token (id, user_id, family_id) ON DELETE RESTRICT,
    CONSTRAINT ck_refresh_token_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED', 'REUSED')),
    CONSTRAINT ck_refresh_token_lifecycle CHECK (
        (status = 'ACTIVE' AND rotated_at IS NULL AND revoked_at IS NULL)
        OR (status IN ('ROTATED', 'REUSED') AND rotated_at IS NOT NULL)
        OR (status IN ('REVOKED', 'EXPIRED') AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_refresh_token_user_status_expires
    ON refresh_token (user_id, status, expires_at, id);
CREATE INDEX idx_refresh_token_family_status
    ON refresh_token (family_id, status, id);
CREATE INDEX idx_refresh_token_expires
    ON refresh_token (status, expires_at, id);
CREATE INDEX idx_refresh_token_updated
    ON refresh_token (updated_at, id);

INSERT INTO app_role (code, name, description, built_in, status)
VALUES
    ('USER', 'User', 'Default authenticated user', TRUE, 'ENABLED'),
    ('ADMIN', 'Administrator', 'Administrative user', TRUE, 'ENABLED'),
    ('SUPER_ADMIN', 'Super administrator', 'Highest platform administrator', TRUE, 'ENABLED');
