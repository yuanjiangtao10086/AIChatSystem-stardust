-- Phase 11F: admin dashboard, audit trail and AI request log browsing.
--
-- No new table: the dashboard is a read-only aggregate over existing tables, and both log
-- endpoints only gained a ``created_at`` range filter. That filter needs a leading-column index
-- on time, because the existing composite indexes all start with another column
-- (``idx_ai_request_status_created``, ``idx_admin_audit_admin_created``, ...).
--
-- Kept as plain ``CREATE INDEX`` statements so the same script runs on MySQL and on the H2
-- instance used by the integration tests (``ALTER TABLE ... ADD INDEX`` is MySQL-only).

CREATE INDEX idx_ai_request_created ON ai_request_log (created_at, id);

CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at, id);
