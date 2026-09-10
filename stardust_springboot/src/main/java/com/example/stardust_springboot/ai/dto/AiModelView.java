package com.example.stardust_springboot.ai.dto;

import com.example.stardust_springboot.ai.entity.AiModel;

/**
 * Model option offered to end users. {@code defaultModel} is the platform default maintained from
 * the admin console, so the chat UI can pre-select it instead of guessing "the first enabled row".
 */
public record AiModelView(String id, String code, String displayName, String provider, boolean defaultModel) {
    public static AiModelView from(AiModel model) {
        return new AiModelView(model.getPublicId(), model.getCode(), model.getDisplayName(),
                model.getProvider().getDisplayName(), model.isDefault());
    }
}
