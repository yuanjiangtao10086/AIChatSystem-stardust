package com.example.stardust_springboot.ai.dto;

import com.example.stardust_springboot.ai.entity.AiModel;

public record AiModelView(String id, String code, String displayName, String provider) {
    public static AiModelView from(AiModel model) {
        return new AiModelView(model.getPublicId(), model.getCode(), model.getDisplayName(),
                model.getProvider().getDisplayName());
    }
}
