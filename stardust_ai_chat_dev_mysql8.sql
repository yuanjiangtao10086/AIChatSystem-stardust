-- 数据库：stardust（Stardust AI Chat SaaS 业务数据库）
-- Stardust AI Chat SaaS - MySQL 8.x development bootstrap
-- Generated from the actual V1-V9 Flyway schema. Development only.
-- Run on an empty MySQL 8 database: mysql -u root -p < stardust_ai_chat_dev_mysql8.sql
-- All application timestamps are UTC. Demo password for every seeded account: password

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';
CREATE DATABASE IF NOT EXISTS stardust
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE stardust;

CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL,
  email_normalized VARCHAR(320) NOT NULL, username VARCHAR(64) NULL,
  password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'NORMAL', ban_reason VARCHAR(500) NULL,
  banned_until DATETIME(3) NULL, password_changed_at DATETIME(3) NULL,
  last_login_at DATETIME(3) NULL, auth_version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_app_user PRIMARY KEY (id),
  CONSTRAINT uk_app_user_public_id UNIQUE (public_id),
  CONSTRAINT uk_app_user_email UNIQUE (email_normalized),
  CONSTRAINT ck_app_user_status CHECK (status IN ('NORMAL','BANNED','DISABLED','DELETED')),
  CONSTRAINT ck_app_user_deleted CHECK ((status='DELETED' AND deleted_at IS NOT NULL) OR (status<>'DELETED' AND deleted_at IS NULL)),
  INDEX idx_app_user_status_created (status,created_at,id),
  INDEX idx_app_user_updated (updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE app_role (
  id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL, description VARCHAR(500) NULL,
  built_in BOOLEAN NOT NULL DEFAULT FALSE, status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_code UNIQUE (code),
  CONSTRAINT ck_app_role_status CHECK (status IN ('ENABLED','DISABLED')),
  INDEX idx_app_role_status_created (status,created_at,id)
) ENGINE=InnoDB;

CREATE TABLE app_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
  granted_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_app_user_role PRIMARY KEY (id), CONSTRAINT uk_user_role UNIQUE (user_id,role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE RESTRICT,
  CONSTRAINT fk_user_role_grantor FOREIGN KEY (granted_by) REFERENCES app_user(id) ON DELETE SET NULL,
  INDEX idx_user_role_role_user (role_id,user_id), INDEX idx_user_role_created (created_at,id)
) ENGINE=InnoDB;

CREATE TABLE ai_provider (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, code VARCHAR(64) NOT NULL,
  display_name VARCHAR(100) NOT NULL, provider_type VARCHAR(32) NOT NULL,
  base_url VARCHAR(500) NOT NULL, credential_ref VARCHAR(255) NULL,
  non_secret_config_json TEXT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
  health_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN', last_health_checked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_provider PRIMARY KEY (id), CONSTRAINT uk_ai_provider_public_id UNIQUE (public_id),
  CONSTRAINT uk_ai_provider_code UNIQUE (code),
  CONSTRAINT ck_ai_provider_status CHECK (status IN ('ENABLED','DISABLED')),
  CONSTRAINT ck_ai_provider_health CHECK (health_status IN ('UNKNOWN','HEALTHY','DEGRADED','UNAVAILABLE')),
  INDEX idx_ai_provider_status_type (status,provider_type), INDEX idx_ai_provider_updated (updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE ai_model (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, provider_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL, external_model_id VARCHAR(128) NOT NULL,
  display_name VARCHAR(100) NOT NULL, model_type VARCHAR(24) NOT NULL,
  capabilities_json TEXT NOT NULL, context_window INTEGER NULL, max_output_tokens INTEGER NULL,
  input_price DECIMAL(19,8) NULL, output_price DECIMAL(19,8) NULL, currency CHAR(3) NULL,
  price_effective_from DATETIME(3) NULL, parameter_policy_json TEXT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED', sort_order INTEGER NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_model PRIMARY KEY (id), CONSTRAINT uk_ai_model_public_id UNIQUE (public_id),
  CONSTRAINT uk_ai_model_code UNIQUE (code), CONSTRAINT uk_ai_model_provider_external UNIQUE (provider_id,external_model_id),
  CONSTRAINT fk_ai_model_provider FOREIGN KEY (provider_id) REFERENCES ai_provider(id) ON DELETE RESTRICT,
  CONSTRAINT ck_ai_model_status CHECK (status IN ('ENABLED','DISABLED')),
  CONSTRAINT ck_ai_model_type CHECK (model_type IN ('CHAT','EMBEDDING','RERANK','MULTIMODAL')),
  CONSTRAINT ck_ai_model_token_limits CHECK ((context_window IS NULL OR context_window>0) AND (max_output_tokens IS NULL OR max_output_tokens>0)),
  CONSTRAINT ck_ai_model_prices CHECK ((input_price IS NULL OR input_price>=0) AND (output_price IS NULL OR output_price>=0)),
  INDEX idx_ai_model_status_type_sort (status,model_type,sort_order),
  INDEX idx_ai_model_provider_status (provider_id,status,sort_order), INDEX idx_ai_model_updated (updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE conversation (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', default_model_id BIGINT NULL,
  last_message_at DATETIME(3) NULL, message_count BIGINT NOT NULL DEFAULT 0,
  next_sequence_no BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_conversation PRIMARY KEY (id), CONSTRAINT uk_conversation_public_id UNIQUE (public_id),
  CONSTRAINT uk_conversation_id_user UNIQUE (id,user_id),
  CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_conversation_default_model FOREIGN KEY (default_model_id) REFERENCES ai_model(id) ON DELETE SET NULL,
  CONSTRAINT ck_conversation_status CHECK (status IN ('ACTIVE','ARCHIVED','DELETED')),
  CONSTRAINT ck_conversation_deleted CHECK ((status='DELETED' AND deleted_at IS NOT NULL) OR (status<>'DELETED' AND deleted_at IS NULL)),
  CONSTRAINT ck_conversation_counts CHECK (message_count>=0 AND next_sequence_no>=1),
  INDEX idx_conversation_user_status_last (user_id,status,last_message_at,id),
  INDEX idx_conversation_user_updated (user_id,updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL,
  conversation_id BIGINT NOT NULL, user_id BIGINT NOT NULL, role VARCHAR(16) NOT NULL,
  sequence_no BIGINT NOT NULL, parent_message_id BIGINT NULL, supersedes_message_id BIGINT NULL,
  variant_no INTEGER NOT NULL DEFAULT 0, content_text LONGTEXT NOT NULL,
  content_format VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN', status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  finish_reason VARCHAR(24) NULL, error_code VARCHAR(64) NULL, model_id BIGINT NULL,
  prompt_tokens BIGINT NULL, completion_tokens BIGINT NULL, total_tokens BIGINT NULL,
  error_message VARCHAR(1000) NULL, checkpoint_seq BIGINT NULL, checkpoint_at DATETIME(3) NULL,
  started_at DATETIME(3) NULL, completed_at DATETIME(3) NULL,
  client_request_id VARCHAR(64) NULL, content_hash CHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_chat_message PRIMARY KEY (id), CONSTRAINT uk_chat_message_public_id UNIQUE (public_id),
  CONSTRAINT uk_chat_message_id_owner UNIQUE (id,conversation_id,user_id),
  CONSTRAINT uk_chat_message_id_user UNIQUE (id,user_id),
  CONSTRAINT uk_message_sequence_variant UNIQUE (conversation_id,sequence_no,variant_no),
  CONSTRAINT uk_message_user_client_request UNIQUE (user_id,client_request_id),
  CONSTRAINT fk_message_conversation_owner FOREIGN KEY (conversation_id,user_id) REFERENCES conversation(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_message_parent_owner FOREIGN KEY (parent_message_id,conversation_id,user_id) REFERENCES chat_message(id,conversation_id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_message_supersedes_owner FOREIGN KEY (supersedes_message_id,conversation_id,user_id) REFERENCES chat_message(id,conversation_id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_message_model FOREIGN KEY (model_id) REFERENCES ai_model(id) ON DELETE SET NULL,
  CONSTRAINT ck_chat_message_role CHECK (role IN ('SYSTEM','USER','ASSISTANT','TOOL')),
  CONSTRAINT ck_chat_message_format CHECK (content_format IN ('PLAIN_TEXT','MARKDOWN','JSON')),
  CONSTRAINT ck_chat_message_status CHECK (status IN ('PENDING','STREAMING','COMPLETED','STOPPED','FAILED','DELETED')),
  CONSTRAINT ck_chat_message_finish CHECK (finish_reason IS NULL OR finish_reason IN ('STOP','LENGTH','USER_CANCELLED','CONTENT_FILTER','TOOL_CALL','ERROR')),
  CONSTRAINT ck_chat_message_deleted CHECK ((status='DELETED' AND deleted_at IS NOT NULL) OR (status<>'DELETED' AND deleted_at IS NULL)),
  CONSTRAINT ck_chat_message_numbers CHECK (sequence_no>=1 AND variant_no>=0 AND (prompt_tokens IS NULL OR prompt_tokens>=0) AND (completion_tokens IS NULL OR completion_tokens>=0) AND (total_tokens IS NULL OR total_tokens>=0) AND (checkpoint_seq IS NULL OR checkpoint_seq>=0)),
  INDEX idx_message_conversation_sequence (conversation_id,sequence_no,id),
  INDEX idx_message_user_status_created (user_id,status,created_at,id),
  INDEX idx_message_parent_variant (parent_message_id,variant_no), INDEX idx_message_updated (updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE refresh_token (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, user_id BIGINT NOT NULL,
  family_id CHAR(26) NOT NULL, token_hash CHAR(64) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  issued_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL, rotated_at DATETIME(3) NULL,
  revoked_at DATETIME(3) NULL, replaced_by_id BIGINT NULL, last_used_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_refresh_token PRIMARY KEY (id), CONSTRAINT uk_refresh_token_public_id UNIQUE (public_id),
  CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
  CONSTRAINT uk_refresh_token_id_user_family UNIQUE (id,user_id,family_id),
  CONSTRAINT uk_refresh_token_replaced_by UNIQUE (replaced_by_id),
  CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_refresh_token_replacement FOREIGN KEY (replaced_by_id,user_id,family_id) REFERENCES refresh_token(id,user_id,family_id) ON DELETE RESTRICT,
  CONSTRAINT ck_refresh_token_status CHECK (status IN ('ACTIVE','ROTATED','REVOKED','EXPIRED','REUSED')),
  CONSTRAINT ck_refresh_token_lifecycle CHECK ((status='ACTIVE' AND rotated_at IS NULL AND revoked_at IS NULL) OR (status IN ('ROTATED','REUSED') AND rotated_at IS NOT NULL) OR (status IN ('REVOKED','EXPIRED') AND revoked_at IS NOT NULL)),
  INDEX idx_refresh_token_user_status_expires (user_id,status,expires_at,id),
  INDEX idx_refresh_token_family_status (family_id,status,id),
  INDEX idx_refresh_token_expires (status,expires_at,id), INDEX idx_refresh_token_updated (updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE ai_request_log (
  id BIGINT NOT NULL AUTO_INCREMENT, request_id CHAR(26) NOT NULL, user_id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL, assistant_message_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL, model_id BIGINT NOT NULL, provider_code VARCHAR(64) NOT NULL,
  model_code VARCHAR(64) NOT NULL, external_model_id VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING', latency_ms BIGINT NULL,
  prompt_tokens BIGINT NULL, completion_tokens BIGINT NULL, total_tokens BIGINT NULL,
  error_code VARCHAR(64) NULL, started_at DATETIME(3) NULL, completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_request_log PRIMARY KEY (id), CONSTRAINT uk_ai_request_log_request UNIQUE (request_id),
  CONSTRAINT uk_ai_request_log_message UNIQUE (assistant_message_id),
  CONSTRAINT fk_ai_request_log_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_ai_request_log_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE RESTRICT,
  CONSTRAINT fk_ai_request_log_message FOREIGN KEY (assistant_message_id) REFERENCES chat_message(id) ON DELETE RESTRICT,
  CONSTRAINT fk_ai_request_log_provider FOREIGN KEY (provider_id) REFERENCES ai_provider(id) ON DELETE RESTRICT,
  CONSTRAINT fk_ai_request_log_model FOREIGN KEY (model_id) REFERENCES ai_model(id) ON DELETE RESTRICT,
  CONSTRAINT ck_ai_request_log_status CHECK (status IN ('PENDING','STREAMING','COMPLETED','STOPPED','FAILED')),
  CONSTRAINT ck_ai_request_log_numbers CHECK ((latency_ms IS NULL OR latency_ms>=0) AND (prompt_tokens IS NULL OR prompt_tokens>=0) AND (completion_tokens IS NULL OR completion_tokens>=0) AND (total_tokens IS NULL OR total_tokens>=0)),
  INDEX idx_ai_request_user_created (user_id,created_at,id),
  INDEX idx_ai_request_conversation_created (conversation_id,created_at,id),
  INDEX idx_ai_request_status_created (status,created_at,id),
  INDEX idx_ai_request_provider_model_created (provider_id,model_id,created_at,id),
  INDEX idx_ai_request_updated (updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE user_storage_usage (
  user_id BIGINT NOT NULL, used_bytes BIGINT NOT NULL DEFAULT 0,
  reserved_bytes BIGINT NOT NULL DEFAULT 0, file_count BIGINT NOT NULL DEFAULT 0,
  quota_bytes BIGINT NOT NULL DEFAULT 1073741824,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_user_storage_usage PRIMARY KEY (user_id),
  CONSTRAINT fk_storage_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_storage_usage_numbers CHECK (used_bytes>=0 AND reserved_bytes>=0 AND file_count>=0 AND quota_bytes>=0 AND used_bytes+reserved_bytes<=quota_bytes),
  INDEX idx_storage_usage_updated (updated_at,user_id)
) ENGINE=InnoDB;

CREATE TABLE user_file (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, user_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL, storage_name VARCHAR(80) NOT NULL,
  object_key VARCHAR(512) NOT NULL, declared_mime VARCHAR(127) NOT NULL,
  detected_mime VARCHAR(127) NOT NULL, extension VARCHAR(16) NOT NULL,
  size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, storage_provider VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'UPLOADING', metadata_json TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_user_file PRIMARY KEY (id), CONSTRAINT uk_user_file_public_id UNIQUE (public_id),
  CONSTRAINT uk_user_file_object UNIQUE (storage_provider,object_key),
  CONSTRAINT uk_user_file_id_owner UNIQUE (id,user_id),
  CONSTRAINT fk_user_file_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_user_file_status CHECK (status IN ('UPLOADING','AVAILABLE','DELETING','FAILED','DELETED')),
  CONSTRAINT ck_user_file_size CHECK (size_bytes>0),
  CONSTRAINT ck_user_file_deleted CHECK ((status='DELETED' AND deleted_at IS NOT NULL) OR (status<>'DELETED' AND deleted_at IS NULL)),
  INDEX idx_user_file_owner_status_created (user_id,status,created_at,id),
  INDEX idx_user_file_owner_updated (user_id,updated_at,id),
  INDEX idx_user_file_owner_hash_size (user_id,sha256,size_bytes),
  INDEX idx_user_file_status_updated (status,updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE chat_message_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT, message_id BIGINT NOT NULL, user_file_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL, attachment_type VARCHAR(16) NOT NULL DEFAULT 'FILE',
  sort_order INTEGER NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT pk_chat_message_attachment PRIMARY KEY (id),
  CONSTRAINT uk_message_attachment UNIQUE (message_id,user_file_id),
  CONSTRAINT fk_attachment_message_owner FOREIGN KEY (message_id,user_id) REFERENCES chat_message(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_attachment_file_owner FOREIGN KEY (user_file_id,user_id) REFERENCES user_file(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_attachment_type CHECK (attachment_type IN ('FILE','IMAGE')),
  CONSTRAINT ck_attachment_order CHECK (sort_order>=0),
  INDEX idx_attachment_message_order (message_id,sort_order,id),
  INDEX idx_attachment_file_message (user_file_id,message_id),
  INDEX idx_attachment_owner_created (user_id,created_at,id)
) ENGINE=InnoDB;

CREATE TABLE conversation_summary (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL,
  conversation_id BIGINT NOT NULL, user_id BIGINT NOT NULL, summary_version BIGINT NOT NULL,
  summary_text TEXT NOT NULL, covered_through_message_id BIGINT NOT NULL,
  covered_through_sequence_no BIGINT NOT NULL, source_message_count INTEGER NOT NULL,
  estimated_tokens INTEGER NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_conversation_summary PRIMARY KEY (id),
  CONSTRAINT uk_conversation_summary_public_id UNIQUE (public_id),
  CONSTRAINT uk_conversation_summary_version UNIQUE (conversation_id,summary_version),
  CONSTRAINT uk_conversation_summary_anchor UNIQUE (conversation_id,covered_through_message_id),
  CONSTRAINT fk_summary_conversation_owner FOREIGN KEY (conversation_id,user_id) REFERENCES conversation(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_summary_message_owner FOREIGN KEY (covered_through_message_id,conversation_id,user_id) REFERENCES chat_message(id,conversation_id,user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_conversation_summary_status CHECK (status IN ('ACTIVE','INVALID')),
  CONSTRAINT ck_conversation_summary_counts CHECK (summary_version>0 AND covered_through_sequence_no>0 AND source_message_count>0 AND estimated_tokens>0),
  INDEX idx_summary_conversation_status_version (conversation_id,status,summary_version,id),
  INDEX idx_summary_owner_updated (user_id,updated_at,id), INDEX idx_summary_anchor_status (covered_through_message_id,status,id)
) ENGINE=InnoDB;

CREATE TABLE user_memory (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, user_id BIGINT NOT NULL,
  content_text TEXT NOT NULL, summary VARCHAR(300) NOT NULL, memory_type VARCHAR(24) NOT NULL,
  importance INTEGER NOT NULL, source_conversation_id BIGINT NULL, source_message_id BIGINT NULL,
  origin VARCHAR(16) NOT NULL, content_hash CHAR(64) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_user_memory PRIMARY KEY (id), CONSTRAINT uk_user_memory_public_id UNIQUE (public_id),
  CONSTRAINT fk_user_memory_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_user_memory_conversation_owner FOREIGN KEY (source_conversation_id,user_id) REFERENCES conversation(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_user_memory_message_owner FOREIGN KEY (source_message_id,source_conversation_id,user_id) REFERENCES chat_message(id,conversation_id,user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_user_memory_type CHECK (memory_type IN ('PREFERENCE','PROJECT','GOAL','EXPLICIT')),
  CONSTRAINT ck_user_memory_origin CHECK (origin IN ('MANUAL','AUTO')),
  CONSTRAINT ck_user_memory_importance CHECK (importance BETWEEN 1 AND 100),
  CONSTRAINT ck_user_memory_source CHECK ((source_message_id IS NULL AND source_conversation_id IS NULL) OR (source_message_id IS NOT NULL AND source_conversation_id IS NOT NULL)),
  CONSTRAINT ck_user_memory_deleted CHECK (deleted_at IS NULL OR (deleted_at IS NOT NULL AND enabled=FALSE)),
  INDEX idx_memory_owner_enabled_importance (user_id,enabled,deleted_at,importance,updated_at,id),
  INDEX idx_memory_owner_type_updated (user_id,memory_type,updated_at,id),
  INDEX idx_memory_owner_hash (user_id,content_hash,deleted_at,id),
  INDEX idx_memory_source_conversation (source_conversation_id,created_at,id),
  INDEX idx_memory_source_message (source_message_id,id)
) ENGINE=InnoDB;

CREATE TABLE knowledge_base (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, user_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL, description VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_knowledge_base PRIMARY KEY (id), CONSTRAINT uk_knowledge_base_public UNIQUE (public_id),
  CONSTRAINT uk_knowledge_base_id_owner UNIQUE (id,user_id),
  CONSTRAINT fk_knowledge_base_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_knowledge_base_status CHECK (status IN ('ACTIVE','DELETED')),
  CONSTRAINT ck_knowledge_base_deleted CHECK ((status='DELETED' AND deleted_at IS NOT NULL) OR (status='ACTIVE' AND deleted_at IS NULL)),
  INDEX idx_kb_owner_status_updated (user_id,status,updated_at,id)
) ENGINE=InnoDB;

CREATE TABLE knowledge_document (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL,
  knowledge_base_id BIGINT NOT NULL, user_id BIGINT NOT NULL, user_file_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED', parser_type VARCHAR(32) NULL,
  chunk_count INTEGER NOT NULL DEFAULT 0, processing_version INTEGER NOT NULL DEFAULT 1,
  embedding_provider VARCHAR(64) NULL, embedding_model VARCHAR(128) NULL,
  error_code VARCHAR(64) NULL, error_message VARCHAR(500) NULL,
  started_at DATETIME(3) NULL, completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_knowledge_document PRIMARY KEY (id),
  CONSTRAINT uk_knowledge_document_public UNIQUE (public_id),
  CONSTRAINT uk_knowledge_document_id_owner UNIQUE (id,knowledge_base_id,user_id),
  CONSTRAINT uk_knowledge_document_file UNIQUE (knowledge_base_id,user_file_id),
  CONSTRAINT fk_knowledge_document_kb_owner FOREIGN KEY (knowledge_base_id,user_id) REFERENCES knowledge_base(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_knowledge_document_file_owner FOREIGN KEY (user_file_id,user_id) REFERENCES user_file(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_knowledge_document_status CHECK (status IN ('UPLOADED','PARSING','PARSED','EMBEDDING','READY','FAILED')),
  CONSTRAINT ck_knowledge_document_counts CHECK (chunk_count>=0 AND processing_version>=1),
  INDEX idx_document_kb_status_created (knowledge_base_id,status,created_at,id),
  INDEX idx_document_owner_status_updated (user_id,status,updated_at,id),
  INDEX idx_document_file_owner (user_file_id,user_id,deleted_at)
) ENGINE=InnoDB;

CREATE TABLE document_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL,
  knowledge_document_id BIGINT NOT NULL, knowledge_base_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  chunk_index INTEGER NOT NULL, content_text TEXT NOT NULL, token_count INTEGER NOT NULL,
  page_number INTEGER NULL, source_metadata_json TEXT NULL, content_hash CHAR(64) NOT NULL,
  processing_version INTEGER NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_document_chunk PRIMARY KEY (id), CONSTRAINT uk_document_chunk_public UNIQUE (public_id),
  CONSTRAINT uk_document_chunk_version UNIQUE (knowledge_document_id,processing_version,chunk_index),
  CONSTRAINT fk_document_chunk_document_owner FOREIGN KEY (knowledge_document_id,knowledge_base_id,user_id) REFERENCES knowledge_document(id,knowledge_base_id,user_id) ON DELETE RESTRICT,
  CONSTRAINT ck_document_chunk_numbers CHECK (chunk_index>=0 AND token_count>0 AND processing_version>=1 AND (page_number IS NULL OR page_number>=1)),
  INDEX idx_chunk_kb_owner (knowledge_base_id,user_id,id),
  INDEX idx_chunk_document_order (knowledge_document_id,processing_version,chunk_index)
) ENGINE=InnoDB;

CREATE TABLE conversation_knowledge_base (
  id BIGINT NOT NULL AUTO_INCREMENT, conversation_id BIGINT NOT NULL,
  knowledge_base_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_conversation_knowledge_base PRIMARY KEY (id),
  CONSTRAINT uk_conversation_knowledge_base UNIQUE (conversation_id,knowledge_base_id),
  CONSTRAINT fk_conversation_kb_conversation_owner FOREIGN KEY (conversation_id,user_id) REFERENCES conversation(id,user_id) ON DELETE RESTRICT,
  CONSTRAINT fk_conversation_kb_knowledge_owner FOREIGN KEY (knowledge_base_id,user_id) REFERENCES knowledge_base(id,user_id) ON DELETE RESTRICT,
  INDEX idx_conversation_kb_owner (user_id,conversation_id),
  INDEX idx_kb_conversations (knowledge_base_id,conversation_id)
) ENGINE=InnoDB;

CREATE TABLE admin_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(26) NOT NULL, admin_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL, target_user_id BIGINT NULL,
  target_resource_type VARCHAR(64) NOT NULL, target_resource_id VARCHAR(64) NULL,
  ip VARCHAR(45) NOT NULL, user_agent VARCHAR(500) NULL, request_id VARCHAR(64) NOT NULL,
  metadata_json TEXT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_admin_audit_log PRIMARY KEY (id),
  CONSTRAINT uk_admin_audit_public_id UNIQUE (public_id),
  CONSTRAINT fk_admin_audit_admin FOREIGN KEY (admin_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_admin_audit_target_user FOREIGN KEY (target_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
  INDEX idx_admin_audit_admin_created (admin_id,created_at,id),
  INDEX idx_admin_audit_target_created (target_resource_type,target_resource_id,created_at,id),
  INDEX idx_admin_audit_user_created (target_user_id,created_at,id),
  INDEX idx_admin_audit_action_created (action,created_at,id),
  INDEX idx_admin_audit_request (request_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 数据字典注释：使用 MySQL 原生 COMMENT 写入表和字段元数据
-- ---------------------------------------------------------------------------
ALTER TABLE app_user
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的 ULID',
  MODIFY email_normalized VARCHAR(320) NOT NULL COMMENT '归一化后的登录邮箱',
  MODIFY username VARCHAR(64) NULL COMMENT '可选用户名',
  MODIFY password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
  MODIFY display_name VARCHAR(100) NOT NULL COMMENT '用户显示名称',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '用户状态：正常、封禁、禁用或删除',
  MODIFY ban_reason VARCHAR(500) NULL COMMENT '封禁原因',
  MODIFY banned_until DATETIME(3) NULL COMMENT '封禁截止时间（UTC）',
  MODIFY password_changed_at DATETIME(3) NULL COMMENT '最近密码修改时间（UTC）',
  MODIFY last_login_at DATETIME(3) NULL COMMENT '最近登录时间（UTC）',
  MODIFY auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '认证版本，修改密码后递增以使旧令牌失效',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='用户账户表';

ALTER TABLE app_role
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY code VARCHAR(64) NOT NULL COMMENT '稳定角色编码',
  MODIFY name VARCHAR(100) NOT NULL COMMENT '角色名称',
  MODIFY description VARCHAR(500) NULL COMMENT '角色说明',
  MODIFY built_in BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为系统内置角色',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '角色启用状态',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='角色字典表';

ALTER TABLE app_user_role
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '用户内部主键',
  MODIFY role_id BIGINT NOT NULL COMMENT '角色内部主键',
  MODIFY granted_by BIGINT NULL COMMENT '授权管理员用户内部主键',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='用户与角色关联表';

ALTER TABLE ai_provider
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的 ULID',
  MODIFY code VARCHAR(64) NOT NULL COMMENT 'Provider 稳定编码',
  MODIFY display_name VARCHAR(100) NOT NULL COMMENT 'Provider 显示名称',
  MODIFY provider_type VARCHAR(32) NOT NULL COMMENT 'Provider 类型',
  MODIFY base_url VARCHAR(500) NOT NULL COMMENT 'Provider API 基础地址',
  MODIFY credential_ref VARCHAR(255) NULL COMMENT '密钥管理器或环境变量引用，不存明文密钥',
  MODIFY non_secret_config_json TEXT NULL COMMENT '非敏感扩展配置 JSON',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'DISABLED' COMMENT '业务启用状态',
  MODIFY health_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康检查状态',
  MODIFY last_health_checked_at DATETIME(3) NULL COMMENT '最近健康检查时间（UTC）',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='AI 服务提供商配置表';

ALTER TABLE ai_model
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的 ULID',
  MODIFY provider_id BIGINT NOT NULL COMMENT '所属 Provider 内部主键',
  MODIFY code VARCHAR(64) NOT NULL COMMENT '平台内模型稳定编码',
  MODIFY external_model_id VARCHAR(128) NOT NULL COMMENT 'Provider 侧模型标识',
  MODIFY display_name VARCHAR(100) NOT NULL COMMENT '模型显示名称',
  MODIFY model_type VARCHAR(24) NOT NULL COMMENT '模型类型：聊天、向量、重排或多模态',
  MODIFY capabilities_json TEXT NOT NULL COMMENT '模型能力列表 JSON',
  MODIFY context_window INTEGER NULL COMMENT '上下文窗口 Token 上限',
  MODIFY max_output_tokens INTEGER NULL COMMENT '最大输出 Token 数',
  MODIFY input_price DECIMAL(19,8) NULL COMMENT '单位输入 Token 价格',
  MODIFY output_price DECIMAL(19,8) NULL COMMENT '单位输出 Token 价格',
  MODIFY currency CHAR(3) NULL COMMENT 'ISO 4217 计价币种',
  MODIFY price_effective_from DATETIME(3) NULL COMMENT '当前价格生效时间（UTC）',
  MODIFY parameter_policy_json TEXT NULL COMMENT '允许的模型参数及范围 JSON',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'DISABLED' COMMENT '模型启用状态',
  MODIFY sort_order INTEGER NOT NULL DEFAULT 0 COMMENT '模型展示排序值',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='AI 模型配置表';

ALTER TABLE conversation
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的 ULID',
  MODIFY user_id BIGINT NOT NULL COMMENT '会话所属用户内部主键',
  MODIFY title VARCHAR(200) NOT NULL COMMENT '会话标题',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：活跃、归档或删除',
  MODIFY default_model_id BIGINT NULL COMMENT '会话默认 AI 模型内部主键',
  MODIFY last_message_at DATETIME(3) NULL COMMENT '最后一条消息时间（UTC）',
  MODIFY message_count BIGINT NOT NULL DEFAULT 0 COMMENT '可重建的消息数量缓存',
  MODIFY next_sequence_no BIGINT NOT NULL DEFAULT 1 COMMENT '下一条消息的原子序号',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='聊天会话表';

ALTER TABLE chat_message
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的 ULID',
  MODIFY conversation_id BIGINT NOT NULL COMMENT '所属会话内部主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '消息所属用户内部主键',
  MODIFY role VARCHAR(16) NOT NULL COMMENT '消息角色：系统、用户、助手或工具',
  MODIFY sequence_no BIGINT NOT NULL COMMENT '会话内逻辑消息序号',
  MODIFY parent_message_id BIGINT NULL COMMENT '父消息内部主键',
  MODIFY supersedes_message_id BIGINT NULL COMMENT '被当前修订替代的消息内部主键',
  MODIFY variant_no INTEGER NOT NULL DEFAULT 0 COMMENT '同一逻辑序号下的变体编号',
  MODIFY content_text LONGTEXT NOT NULL COMMENT '消息正文',
  MODIFY content_format VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN' COMMENT '正文格式',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '消息生成状态',
  MODIFY finish_reason VARCHAR(24) NULL COMMENT '模型生成结束原因',
  MODIFY error_code VARCHAR(64) NULL COMMENT '生成失败错误码',
  MODIFY model_id BIGINT NULL COMMENT '生成该消息的 AI 模型内部主键',
  MODIFY prompt_tokens BIGINT NULL COMMENT '输入 Token 数',
  MODIFY completion_tokens BIGINT NULL COMMENT '输出 Token 数',
  MODIFY total_tokens BIGINT NULL COMMENT '总 Token 数',
  MODIFY error_message VARCHAR(1000) NULL COMMENT '脱敏后的生成错误说明',
  MODIFY checkpoint_seq BIGINT NULL COMMENT '流式内容最近持久化检查点序号',
  MODIFY checkpoint_at DATETIME(3) NULL COMMENT '最近流式检查点时间（UTC）',
  MODIFY started_at DATETIME(3) NULL COMMENT '开始生成时间（UTC）',
  MODIFY completed_at DATETIME(3) NULL COMMENT '生成终止时间（UTC）',
  MODIFY client_request_id VARCHAR(64) NULL COMMENT '客户端幂等请求标识',
  MODIFY content_hash CHAR(64) NULL COMMENT '正文 SHA-256 哈希',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='聊天消息事实表';

ALTER TABLE refresh_token
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '会话令牌公开 ULID',
  MODIFY user_id BIGINT NOT NULL COMMENT '令牌所属用户内部主键',
  MODIFY family_id CHAR(26) NOT NULL COMMENT '轮换令牌族 ULID',
  MODIFY token_hash CHAR(64) NOT NULL COMMENT '带服务端 Pepper 的令牌哈希',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '刷新令牌生命周期状态',
  MODIFY issued_at DATETIME(3) NOT NULL COMMENT '签发时间（UTC）',
  MODIFY expires_at DATETIME(3) NOT NULL COMMENT '过期时间（UTC）',
  MODIFY rotated_at DATETIME(3) NULL COMMENT '轮换时间（UTC）',
  MODIFY revoked_at DATETIME(3) NULL COMMENT '撤销或失效时间（UTC）',
  MODIFY replaced_by_id BIGINT NULL COMMENT '轮换后的新令牌内部主键',
  MODIFY last_used_at DATETIME(3) NULL COMMENT '最近使用时间（UTC）',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='刷新令牌及轮换会话表';

ALTER TABLE ai_request_log
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY request_id CHAR(26) NOT NULL COMMENT '贯穿三端的 AI 请求 ULID',
  MODIFY user_id BIGINT NOT NULL COMMENT '发起用户内部主键',
  MODIFY conversation_id BIGINT NOT NULL COMMENT '关联会话内部主键',
  MODIFY assistant_message_id BIGINT NOT NULL COMMENT '关联助手占位消息内部主键',
  MODIFY provider_id BIGINT NOT NULL COMMENT '请求时 Provider 内部主键',
  MODIFY model_id BIGINT NOT NULL COMMENT '请求时模型内部主键',
  MODIFY provider_code VARCHAR(64) NOT NULL COMMENT '请求时 Provider 编码快照',
  MODIFY model_code VARCHAR(64) NOT NULL COMMENT '请求时模型编码快照',
  MODIFY external_model_id VARCHAR(128) NOT NULL COMMENT '请求时外部模型标识快照',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'AI 请求执行状态',
  MODIFY latency_ms BIGINT NULL COMMENT '端到端延迟毫秒数',
  MODIFY prompt_tokens BIGINT NULL COMMENT '输入 Token 数',
  MODIFY completion_tokens BIGINT NULL COMMENT '输出 Token 数',
  MODIFY total_tokens BIGINT NULL COMMENT '总 Token 数',
  MODIFY error_code VARCHAR(64) NULL COMMENT '标准化错误码',
  MODIFY started_at DATETIME(3) NULL COMMENT '开始调用时间（UTC）',
  MODIFY completed_at DATETIME(3) NULL COMMENT '请求终止时间（UTC）',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='AI 请求及用量日志表';

ALTER TABLE user_storage_usage
  MODIFY user_id BIGINT NOT NULL COMMENT '用户内部主键，同时作为本表主键',
  MODIFY used_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '已实际占用字节数',
  MODIFY reserved_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '并发上传预留字节数',
  MODIFY file_count BIGINT NOT NULL DEFAULT 0 COMMENT '有效文件数量',
  MODIFY quota_bytes BIGINT NOT NULL DEFAULT 1073741824 COMMENT '用户存储配额字节数',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '用量更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT '并发更新乐观锁版本号',
  COMMENT='用户云盘容量汇总表';

ALTER TABLE user_file
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的文件 ULID',
  MODIFY user_id BIGINT NOT NULL COMMENT '文件所属用户内部主键',
  MODIFY original_name VARCHAR(255) NOT NULL COMMENT '用户可见原始文件名',
  MODIFY storage_name VARCHAR(80) NOT NULL COMMENT '服务端生成的安全存储文件名',
  MODIFY object_key VARCHAR(512) NOT NULL COMMENT '对象存储键或本地相对路径',
  MODIFY declared_mime VARCHAR(127) NOT NULL COMMENT '客户端声明的 MIME 类型',
  MODIFY detected_mime VARCHAR(127) NOT NULL COMMENT '服务端检测的 MIME 类型',
  MODIFY extension VARCHAR(16) NOT NULL COMMENT '归一化文件扩展名',
  MODIFY size_bytes BIGINT NOT NULL COMMENT '文件大小（字节）',
  MODIFY sha256 CHAR(64) NOT NULL COMMENT '文件内容 SHA-256',
  MODIFY storage_provider VARCHAR(32) NOT NULL COMMENT '存储 Provider 编码',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'UPLOADING' COMMENT '文件生命周期状态',
  MODIFY metadata_json TEXT NULL COMMENT '预览和多模态扩展元数据 JSON',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='统一用户文件与云盘元数据表';

ALTER TABLE chat_message_attachment
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY message_id BIGINT NOT NULL COMMENT '关联聊天消息内部主键',
  MODIFY user_file_id BIGINT NOT NULL COMMENT '引用的统一用户文件内部主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '冗余所属用户主键，用于数据库级归属校验',
  MODIFY attachment_type VARCHAR(16) NOT NULL DEFAULT 'FILE' COMMENT '附件类型：普通文件或图片',
  MODIFY sort_order INTEGER NOT NULL DEFAULT 0 COMMENT '附件展示顺序',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '引用创建时间（UTC）',
  COMMENT='聊天消息附件引用表';

ALTER TABLE conversation_summary
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '摘要版本公开 ULID',
  MODIFY conversation_id BIGINT NOT NULL COMMENT '所属会话内部主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '所属用户内部主键',
  MODIFY summary_version BIGINT NOT NULL COMMENT '会话摘要版本号',
  MODIFY summary_text TEXT NOT NULL COMMENT '压缩后的短期会话摘要',
  MODIFY covered_through_message_id BIGINT NOT NULL COMMENT '摘要覆盖到的锚点消息内部主键',
  MODIFY covered_through_sequence_no BIGINT NOT NULL COMMENT '摘要覆盖到的消息序号',
  MODIFY source_message_count INTEGER NOT NULL COMMENT '参与摘要的原始消息数量',
  MODIFY estimated_tokens INTEGER NOT NULL COMMENT '摘要估算 Token 数',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '摘要状态：有效或失效',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='Conversation Short Memory 版本摘要表';

ALTER TABLE user_memory
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的记忆 ULID',
  MODIFY user_id BIGINT NOT NULL COMMENT '记忆所属用户内部主键',
  MODIFY content_text TEXT NOT NULL COMMENT '长期记忆完整内容',
  MODIFY summary VARCHAR(300) NOT NULL COMMENT '记忆摘要',
  MODIFY memory_type VARCHAR(24) NOT NULL COMMENT '记忆类型：偏好、项目、目标或明确记忆',
  MODIFY importance INTEGER NOT NULL COMMENT '重要程度，范围 1 到 100',
  MODIFY source_conversation_id BIGINT NULL COMMENT '来源会话内部主键',
  MODIFY source_message_id BIGINT NULL COMMENT '来源消息内部主键',
  MODIFY origin VARCHAR(16) NOT NULL COMMENT '来源：用户手动或系统自动提取',
  MODIFY content_hash CHAR(64) NOT NULL COMMENT '归一化记忆内容 SHA-256',
  MODIFY enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否参与检索与上下文构建',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='用户长期记忆表';

ALTER TABLE knowledge_base
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的知识库 ULID',
  MODIFY user_id BIGINT NOT NULL COMMENT '知识库所属用户内部主键',
  MODIFY name VARCHAR(120) NOT NULL COMMENT '知识库名称',
  MODIFY description VARCHAR(1000) NULL COMMENT '知识库说明',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '知识库状态：有效或删除',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='用户知识库表';

ALTER TABLE knowledge_document
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的知识文档 ULID',
  MODIFY knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库内部主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '文档所属用户内部主键',
  MODIFY user_file_id BIGINT NOT NULL COMMENT '引用的统一用户文件内部主键',
  MODIFY status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED' COMMENT '解析和向量化流水线状态',
  MODIFY parser_type VARCHAR(32) NULL COMMENT '实际使用的文档解析器类型',
  MODIFY chunk_count INTEGER NOT NULL DEFAULT 0 COMMENT '当前处理版本的文本块数量',
  MODIFY processing_version INTEGER NOT NULL DEFAULT 1 COMMENT '文档重试处理版本号',
  MODIFY embedding_provider VARCHAR(64) NULL COMMENT '向量生成 Provider 编码',
  MODIFY embedding_model VARCHAR(128) NULL COMMENT '向量模型标识',
  MODIFY error_code VARCHAR(64) NULL COMMENT '处理失败错误码',
  MODIFY error_message VARCHAR(500) NULL COMMENT '脱敏后的处理错误说明',
  MODIFY started_at DATETIME(3) NULL COMMENT '处理开始时间（UTC）',
  MODIFY completed_at DATETIME(3) NULL COMMENT '处理完成或失败时间（UTC）',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY deleted_at DATETIME(3) NULL COMMENT '软删除时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='知识库文档处理状态表';

ALTER TABLE document_chunk
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '对外公开的文本块 ULID',
  MODIFY knowledge_document_id BIGINT NOT NULL COMMENT '所属知识文档内部主键',
  MODIFY knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库内部主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '所属用户内部主键',
  MODIFY chunk_index INTEGER NOT NULL COMMENT '文档版本内的文本块顺序',
  MODIFY content_text TEXT NOT NULL COMMENT '清洗和切分后的文本内容',
  MODIFY token_count INTEGER NOT NULL COMMENT '文本块 Token 数',
  MODIFY page_number INTEGER NULL COMMENT '来源页码',
  MODIFY source_metadata_json TEXT NULL COMMENT '来源位置等扩展元数据 JSON',
  MODIFY content_hash CHAR(64) NOT NULL COMMENT '文本块内容 SHA-256',
  MODIFY processing_version INTEGER NOT NULL COMMENT '所属文档处理版本号',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='RAG 文档文本块事实表';

ALTER TABLE conversation_knowledge_base
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY conversation_id BIGINT NOT NULL COMMENT '绑定的会话内部主键',
  MODIFY knowledge_base_id BIGINT NOT NULL COMMENT '绑定的知识库内部主键',
  MODIFY user_id BIGINT NOT NULL COMMENT '双方共同所属用户内部主键',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='聊天会话与知识库绑定表';

ALTER TABLE admin_audit_log
  MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部自增主键',
  MODIFY public_id CHAR(26) NOT NULL COMMENT '审计记录公开 ULID',
  MODIFY admin_id BIGINT NOT NULL COMMENT '执行操作的管理员内部主键',
  MODIFY action VARCHAR(64) NOT NULL COMMENT '管理员动作编码',
  MODIFY target_user_id BIGINT NULL COMMENT '被操作资源所属用户内部主键',
  MODIFY target_resource_type VARCHAR(64) NOT NULL COMMENT '目标资源类型',
  MODIFY target_resource_id VARCHAR(64) NULL COMMENT '目标资源公开标识',
  MODIFY ip VARCHAR(45) NOT NULL COMMENT '请求来源 IP 地址',
  MODIFY user_agent VARCHAR(500) NULL COMMENT '请求客户端 User-Agent',
  MODIFY request_id VARCHAR(64) NOT NULL COMMENT '关联 HTTP 请求标识',
  MODIFY metadata_json TEXT NULL COMMENT '不含敏感正文的操作差异元数据 JSON',
  MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计创建时间（UTC）',
  MODIFY updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录更新时间（UTC）',
  MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本号',
  COMMENT='管理员敏感操作审计日志表';

-- ---------------------------------------------------------------------------
-- Development seed data
-- All four accounts use BCrypt password "password". Never load this section
-- in staging or production. AVAILABLE file rows are metadata fixtures only;
-- matching objects are not created in LocalStorage by this SQL script.
-- ---------------------------------------------------------------------------
START TRANSACTION;

INSERT INTO app_role (id,code,name,description,built_in,status) VALUES
  (1,'USER','User','Default authenticated user',TRUE,'ENABLED'),
  (2,'ADMIN','Administrator','Administrative user',TRUE,'ENABLED'),
  (3,'SUPER_ADMIN','Super administrator','Highest platform administrator',TRUE,'ENABLED');

INSERT INTO app_user
  (id,public_id,email_normalized,username,password_hash,display_name,status,ban_reason,banned_until,password_changed_at,last_login_at,created_at,updated_at,auth_version,version)
VALUES
  (1,'00000000000000000000000001','superadmin@stardust.local','superadmin','$2a$10$dXJ3SW6G7P50lGmMkkmwe.20rC.6LxA52HQ1qRkK21FIhI.7T6I','Super Admin','NORMAL',NULL,NULL,UTC_TIMESTAMP(3)-INTERVAL 30 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 90 DAY,UTC_TIMESTAMP(3),0,0),
  (2,'00000000000000000000000002','admin@stardust.local','admin','$2a$10$dXJ3SW6G7P50lGmMkkmwe.20rC.6LxA52HQ1qRkK21FIhI.7T6I','Operations Admin','NORMAL',NULL,NULL,UTC_TIMESTAMP(3)-INTERVAL 20 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 HOUR,UTC_TIMESTAMP(3)-INTERVAL 60 DAY,UTC_TIMESTAMP(3),0,0),
  (3,'00000000000000000000000003','alice@stardust.local','alice','$2a$10$dXJ3SW6G7P50lGmMkkmwe.20rC.6LxA52HQ1qRkK21FIhI.7T6I','Alice Chen','NORMAL',NULL,NULL,UTC_TIMESTAMP(3)-INTERVAL 10 DAY,UTC_TIMESTAMP(3)-INTERVAL 30 MINUTE,UTC_TIMESTAMP(3)-INTERVAL 15 DAY,UTC_TIMESTAMP(3),0,0),
  (4,'00000000000000000000000004','bob@stardust.local','bob','$2a$10$dXJ3SW6G7P50lGmMkkmwe.20rC.6LxA52HQ1qRkK21FIhI.7T6I','Bob Liu','BANNED','Repeated policy violations',UTC_TIMESTAMP(3)+INTERVAL 7 DAY,UTC_TIMESTAMP(3)-INTERVAL 8 DAY,UTC_TIMESTAMP(3)-INTERVAL 3 DAY,UTC_TIMESTAMP(3)-INTERVAL 12 DAY,UTC_TIMESTAMP(3),0,0);

INSERT INTO app_user_role (id,user_id,role_id,granted_by) VALUES
  (1,1,1,NULL),(2,1,2,NULL),(3,1,3,NULL),
  (4,2,1,1),(5,2,2,1),(6,3,1,2),(7,4,1,2);

INSERT INTO ai_provider
  (id,public_id,code,display_name,provider_type,base_url,credential_ref,non_secret_config_json,status,health_status,last_health_checked_at)
VALUES
  (1,'00000000000000000000000010','openai-compatible','OpenAI Compatible','OPENAI_COMPATIBLE','https://api.openai.com/v1','env:OPENAI_API_KEY','{"connectTimeoutSeconds":10}','ENABLED','UNKNOWN',NULL);

INSERT INTO ai_model
  (id,public_id,provider_id,code,external_model_id,display_name,model_type,capabilities_json,context_window,max_output_tokens,input_price,output_price,currency,price_effective_from,parameter_policy_json,status,sort_order)
VALUES
  (1,'00000000000000000000000011',1,'dev-chat','gpt-4.1-mini','Development Chat','CHAT','["streaming","reasoning"]',128000,4096,0.00000040,0.00000160,'USD',UTC_TIMESTAMP(3)-INTERVAL 30 DAY,'{"temperature":{"min":0,"max":2,"default":0.7}}','ENABLED',10);

INSERT INTO conversation
  (id,public_id,user_id,title,status,default_model_id,last_message_at,message_count,next_sequence_no,created_at,updated_at)
VALUES
  (1,'00000000000000000000000020',3,'Designing a reliable streaming API','ACTIVE',1,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,4,5,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR),
  (2,'00000000000000000000000021',4,'Travel checklist','ARCHIVED',1,UTC_TIMESTAMP(3)-INTERVAL 4 DAY,2,3,UTC_TIMESTAMP(3)-INTERVAL 5 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY);

INSERT INTO chat_message
  (id,public_id,conversation_id,user_id,role,sequence_no,variant_no,content_text,content_format,status,client_request_id,content_hash,created_at,updated_at,completed_at)
VALUES
  (1,'00000000000000000000000030',1,3,'USER',1,0,'How should an SSE chat API represent partial output?','MARKDOWN','COMPLETED','dev-alice-001','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY),
  (3,'00000000000000000000000032',1,3,'USER',3,0,'Also include cancellation and token usage.','MARKDOWN','COMPLETED','dev-alice-002','cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR),
  (5,'00000000000000000000000034',2,4,'USER',1,0,'Create a compact travel checklist.','MARKDOWN','COMPLETED','dev-bob-001','eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',UTC_TIMESTAMP(3)-INTERVAL 5 DAY,UTC_TIMESTAMP(3)-INTERVAL 5 DAY,UTC_TIMESTAMP(3)-INTERVAL 5 DAY);

INSERT INTO chat_message
  (id,public_id,conversation_id,user_id,role,sequence_no,parent_message_id,variant_no,content_text,content_format,status,finish_reason,model_id,prompt_tokens,completion_tokens,total_tokens,content_hash,started_at,completed_at,created_at,updated_at)
VALUES
  (2,'00000000000000000000000031',1,3,'ASSISTANT',2,1,0,'Use typed `start`, `delta`, `usage`, `done`, and `error` events carrying one requestId. Persist the final accumulated answer instead of every delta.','MARKDOWN','COMPLETED','STOP',1,42,58,100,'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY),
  (4,'00000000000000000000000033',1,3,'ASSISTANT',4,3,0,'On cancellation, stop forwarding immediately, notify the provider, and converge the placeholder to `STOPPED`. Emit usage once when known.','MARKDOWN','COMPLETED','STOP',1,64,44,108,'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR),
  (6,'00000000000000000000000035',2,4,'ASSISTANT',2,5,0,'- Passport and tickets\n- Chargers\n- Medication\n- Offline maps','MARKDOWN','COMPLETED','STOP',1,21,24,45,'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',UTC_TIMESTAMP(3)-INTERVAL 4 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY);

INSERT INTO ai_request_log
  (id,request_id,user_id,conversation_id,assistant_message_id,provider_id,model_id,provider_code,model_code,external_model_id,status,latency_ms,prompt_tokens,completion_tokens,total_tokens,started_at,completed_at,created_at,updated_at)
VALUES
  (1,'00000000000000000000000040',3,1,2,1,1,'openai-compatible','dev-chat','gpt-4.1-mini','COMPLETED',1240,42,58,100,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY),
  (2,'00000000000000000000000041',3,1,4,1,1,'openai-compatible','dev-chat','gpt-4.1-mini','COMPLETED',890,64,44,108,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR),
  (3,'00000000000000000000000042',4,2,6,1,1,'openai-compatible','dev-chat','gpt-4.1-mini','COMPLETED',760,21,24,45,UTC_TIMESTAMP(3)-INTERVAL 4 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY,UTC_TIMESTAMP(3)-INTERVAL 4 DAY);

INSERT INTO refresh_token
  (id,public_id,user_id,family_id,token_hash,status,issued_at,expires_at,revoked_at,last_used_at)
VALUES
  (1,'00000000000000000000000050',3,'00000000000000000000000051','0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef','REVOKED',UTC_TIMESTAMP(3)-INTERVAL 8 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY);

INSERT INTO user_storage_usage (user_id,used_bytes,reserved_bytes,file_count,quota_bytes) VALUES
  (1,0,0,0,5368709120),(2,0,0,0,5368709120),(3,3400,0,2,1073741824),(4,800,0,1,1073741824);

INSERT INTO user_file
  (id,public_id,user_id,original_name,storage_name,object_key,declared_mime,detected_mime,extension,size_bytes,sha256,storage_provider,status,metadata_json,created_at,updated_at)
VALUES
  (1,'00000000000000000000000060',3,'streaming-diagram.png','00000000000000000000000060.png','00000000000000000000000003/2026/09/00000000000000000000000060.png','image/png','image/png','png',1200,'1111111111111111111111111111111111111111111111111111111111111111','local','AVAILABLE','{"category":"image","previewable":true}',UTC_TIMESTAMP(3)-INTERVAL 2 DAY,UTC_TIMESTAMP(3)-INTERVAL 2 DAY),
  (2,'00000000000000000000000061',3,'engineering-handbook.txt','00000000000000000000000061.txt','00000000000000000000000003/2026/09/00000000000000000000000061.txt','text/plain','text/plain','txt',2200,'2222222222222222222222222222222222222222222222222222222222222222','local','AVAILABLE','{"category":"document","previewable":false}',UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY),
  (3,'00000000000000000000000062',4,'travel-notes.txt','00000000000000000000000062.txt','00000000000000000000000004/2026/09/00000000000000000000000062.txt','text/plain','text/plain','txt',800,'3333333333333333333333333333333333333333333333333333333333333333','local','AVAILABLE','{"category":"document","previewable":false}',UTC_TIMESTAMP(3)-INTERVAL 5 DAY,UTC_TIMESTAMP(3)-INTERVAL 5 DAY);

INSERT INTO chat_message_attachment (id,message_id,user_file_id,user_id,attachment_type,sort_order,created_at)
VALUES (1,1,1,3,'IMAGE',0,UTC_TIMESTAMP(3)-INTERVAL 2 DAY);

INSERT INTO conversation_summary
  (id,public_id,conversation_id,user_id,summary_version,summary_text,covered_through_message_id,covered_through_sequence_no,source_message_count,estimated_tokens,status,created_at,updated_at)
VALUES
  (1,'00000000000000000000000070',1,3,1,'Alice is designing a typed SSE contract and prefers end-of-stream persistence.',2,2,2,18,'ACTIVE',UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY);

INSERT INTO user_memory
  (id,public_id,user_id,content_text,summary,memory_type,importance,source_conversation_id,source_message_id,origin,content_hash,enabled,created_at,updated_at)
VALUES
  (1,'00000000000000000000000071',3,'Alice prefers TypeScript examples and concise architecture diagrams.','Prefers TypeScript and concise diagrams','PREFERENCE',82,NULL,NULL,'MANUAL','4444444444444444444444444444444444444444444444444444444444444444',TRUE,UTC_TIMESTAMP(3)-INTERVAL 10 DAY,UTC_TIMESTAMP(3)-INTERVAL 10 DAY),
  (2,'00000000000000000000000072',3,'Alice is building the Stardust AI Chat SaaS as a long-term project.','Building Stardust AI Chat SaaS','PROJECT',94,1,3,'AUTO','5555555555555555555555555555555555555555555555555555555555555555',TRUE,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR,UTC_TIMESTAMP(3)-INTERVAL 1 HOUR),
  (3,'00000000000000000000000073',4,'Bob prefers aisle seats.','Prefers aisle seats','PREFERENCE',60,NULL,NULL,'MANUAL','6666666666666666666666666666666666666666666666666666666666666666',FALSE,UTC_TIMESTAMP(3)-INTERVAL 8 DAY,UTC_TIMESTAMP(3)-INTERVAL 8 DAY);

INSERT INTO knowledge_base
  (id,public_id,user_id,name,description,status,created_at,updated_at)
VALUES
  (1,'00000000000000000000000080',3,'Engineering Handbook','Architecture and operations guidance for the Stardust project.','ACTIVE',UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY);

INSERT INTO knowledge_document
  (id,public_id,knowledge_base_id,user_id,user_file_id,status,parser_type,chunk_count,processing_version,embedding_provider,embedding_model,started_at,completed_at,created_at,updated_at)
VALUES
  (1,'00000000000000000000000081',1,3,2,'READY','plain-text',2,1,'openai-compatible','text-embedding-3-small',UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY);

INSERT INTO document_chunk
  (id,public_id,knowledge_document_id,knowledge_base_id,user_id,chunk_index,content_text,token_count,page_number,source_metadata_json,content_hash,processing_version,created_at,updated_at)
VALUES
  (1,'00000000000000000000000082',1,1,3,0,'Streaming responses use start, delta, usage, done, and error events.',15,1,'{"section":"Streaming"}','7777777777777777777777777777777777777777777777777777777777777777',1,UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY),
  (2,'00000000000000000000000083',1,1,3,1,'Ownership checks always include the authenticated user id in the repository query.',16,2,'{"section":"Security"}','8888888888888888888888888888888888888888888888888888888888888888',1,UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY);

INSERT INTO conversation_knowledge_base
  (id,conversation_id,knowledge_base_id,user_id,created_at,updated_at)
VALUES (1,1,1,3,UTC_TIMESTAMP(3)-INTERVAL 1 DAY,UTC_TIMESTAMP(3)-INTERVAL 1 DAY);

INSERT INTO admin_audit_log
  (id,public_id,admin_id,action,target_user_id,target_resource_type,target_resource_id,ip,user_agent,request_id,metadata_json,created_at,updated_at)
VALUES
  (1,'00000000000000000000000090',2,'CONVERSATION_VIEW',3,'CONVERSATION','00000000000000000000000020','127.0.0.1','Stardust development seed','00000000000000000000000091','{"reason":"support investigation"}',UTC_TIMESTAMP(3)-INTERVAL 30 MINUTE,UTC_TIMESTAMP(3)-INTERVAL 30 MINUTE),
  (2,'00000000000000000000000092',1,'USER_BAN',4,'USER','00000000000000000000000004','127.0.0.1','Stardust development seed','00000000000000000000000093','{"reason":"Repeated policy violations"}',UTC_TIMESTAMP(3)-INTERVAL 3 DAY,UTC_TIMESTAMP(3)-INTERVAL 3 DAY);

COMMIT;

-- Quick verification: should return 19 tables.
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema='stardust' AND table_type='BASE TABLE';

SELECT u.email_normalized,u.status,GROUP_CONCAT(r.code ORDER BY r.id) AS roles
FROM app_user u
JOIN app_user_role ur ON ur.user_id=u.id
JOIN app_role r ON r.id=ur.role_id
GROUP BY u.id,u.email_normalized,u.status
ORDER BY u.id;
