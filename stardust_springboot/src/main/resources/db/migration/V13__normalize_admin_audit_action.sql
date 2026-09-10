-- Stage 11B renamed two audit action names in code without migrating the rows that already existed
-- (and the development seed still ships the old names):
--   CONVERSATION_VIEW   -> VIEW_CHAT_MESSAGES   ("opened the message list")
--   CONVERSATION_DELETE -> DELETE_CONVERSATION
-- AdminAuditAction is persisted with @Enumerated(STRING); an unknown value makes every read of
-- admin_audit_log fail with 50002 "persistence operation failed" instead of showing the audit feed.
-- This migration normalizes historical rows; AdminAuditActionConverter keeps any future unknown
-- value from breaking reads again.
UPDATE admin_audit_log SET action = 'VIEW_CHAT_MESSAGES' WHERE action = 'CONVERSATION_VIEW';
UPDATE admin_audit_log SET action = 'DELETE_CONVERSATION' WHERE action = 'CONVERSATION_DELETE';
