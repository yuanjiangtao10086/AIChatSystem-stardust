package com.example.stardust_springboot.admin.audit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Lenient mapping between {@link AdminAuditAction} and the {@code admin_audit_log.action} column.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} throws {@code IllegalArgumentException} when the stored
 * value is not part of the enum. Because the audit table is append-only and survives releases that
 * rename actions, one legacy row used to make the entire audit feed (and any other read of that
 * table) fail with {@code 50002 persistence operation failed} instead of reporting the real state.
 *
 * <p>Historical rows are normalized by {@code V13__normalize_admin_audit_action.sql}; this converter
 * is the second line of defence so an unrecognized value degrades to {@link AdminAuditAction#LEGACY}
 * instead of breaking the request. Writing always uses the enum name, so no new legacy value can be
 * produced by this application.
 */
@Converter
public class AdminAuditActionConverter implements AttributeConverter<AdminAuditAction, String> {

    @Override
    public String convertToDatabaseColumn(AdminAuditAction attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AdminAuditAction convertToEntityAttribute(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        try {
            return AdminAuditAction.valueOf(storedValue);
        } catch (IllegalArgumentException unknownAction) {
            return AdminAuditAction.LEGACY;
        }
    }
}
