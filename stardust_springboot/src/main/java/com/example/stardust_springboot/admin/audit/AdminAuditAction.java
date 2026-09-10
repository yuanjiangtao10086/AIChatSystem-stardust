package com.example.stardust_springboot.admin.audit;

public enum AdminAuditAction {
    USER_CREATE, USER_UPDATE, USER_BAN, USER_UNBAN, USER_DISABLE, USER_ENABLE, USER_RESTORE,
    USER_DELETE, USER_RESET_PASSWORD, USER_ROLES_UPDATE, USER_USAGE_ADJUST,
    VIEW_CONVERSATION, VIEW_CHAT_MESSAGES, VIEW_USER_FILE, VIEW_KNOWLEDGE_BASE,
    DELETE_CONVERSATION, DELETE_CHAT_MESSAGE,
    FILE_DELETE, FILE_DOWNLOAD, RAG_DOCUMENT_RETRY, RAG_DOCUMENT_DELETE, RAG_VECTOR_REMOVE,
    PROVIDER_CREATE, PROVIDER_UPDATE, PROVIDER_STATUS_UPDATE, PROVIDER_DELETE,
    MODEL_CREATE, MODEL_UPDATE, MODEL_STATUS_UPDATE, MODEL_DELETE, MODEL_DEFAULT_UPDATE, MODEL_REORDER,
    /**
     * Tombstone for an audit row whose stored action no longer exists in this enum (for example a
     * value renamed by a later release). Never produced by application code — it only exists so a
     * single unparseable row cannot turn the whole audit feed into a 50002 persistence error.
     */
    LEGACY
}
