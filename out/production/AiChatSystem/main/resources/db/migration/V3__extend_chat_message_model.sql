ALTER TABLE chat_message
    ADD COLUMN total_tokens BIGINT NULL;

ALTER TABLE chat_message
    ADD COLUMN error_message VARCHAR(1000) NULL;

ALTER TABLE chat_message
    ADD CONSTRAINT ck_chat_message_total_tokens CHECK (total_tokens IS NULL OR total_tokens >= 0);
