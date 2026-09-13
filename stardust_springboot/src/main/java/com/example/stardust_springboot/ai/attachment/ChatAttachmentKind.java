package com.example.stardust_springboot.ai.attachment;

/**
 * How a chat attachment is delivered to the AI service.
 *
 * <p>The kind is decided by Spring, which owns the file metadata, the model capability catalog and
 * the storage access. Python must render each kind faithfully instead of guessing from a MIME type.
 */
public enum ChatAttachmentKind {
    /** Image bytes delivered as base64 so the provider can build a vision content part. */
    IMAGE,
    /** Document text extracted by Spring and delivered as plain text. */
    TEXT,
    /** The file is referenced by the message but cannot be delivered to the model. */
    UNSUPPORTED
}
