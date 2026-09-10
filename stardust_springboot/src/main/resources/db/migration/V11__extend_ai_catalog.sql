-- Phase 11E: administrator AI provider / model management.
--
-- The provider table needs no new column: credentials stay external ``credential_ref``
-- references (ADR-016) and non-secret options such as timeouts live in
-- ``non_secret_config_json``. Models need one explicit platform default per model type so the
-- chat model picker has a deterministic pre-selection instead of "first row wins".
--
-- ``ai_model.capabilities_json`` keeps its column but changes shape from a string array
-- (``["streaming","reasoning"]``) to a typed object
-- (``{"streaming":true,"vision":false,"reasoning":true,"embedding":false}``). Readers tolerate
-- the legacy array form, so no data rewrite is required.

ALTER TABLE ai_model
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_ai_model_default ON ai_model (model_type, is_default, sort_order);
