-- Phase 14 introduced the assistant-generated attachment type AttachmentType.OUTPUT
-- (see MessageArtifactService.persist), but the ck_attachment_type check constraint
-- created in V5 only allowed ('FILE', 'IMAGE'). That made artifact persistence fail with
-- "Check constraint 'ck_attachment_type' is violated" whenever an AI artifact was saved.
-- Extend the constraint to accept the OUTPUT type. V5 is already applied, so a new
-- migration (not an edit to V5) is required to avoid a Flyway checksum mismatch.
ALTER TABLE chat_message_attachment
    DROP CHECK ck_attachment_type;

ALTER TABLE chat_message_attachment
    ADD CONSTRAINT ck_attachment_type
    CHECK (attachment_type IN ('FILE', 'IMAGE', 'OUTPUT'));
