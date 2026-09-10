package com.example.stardust_springboot.ai.catalog;

import java.math.BigDecimal;

/**
 * Typed generation defaults for a model, persisted as {@code ai_model.parameter_policy_json}.
 *
 * <p>Only knobs the platform actually understands are modelled here. Anything else stays out of
 * the schema instead of becoming a free-form JSON textarea: ranges are validated by
 * {@code AdminDtos.ModelRequest} before they reach the entity.
 */
public record ModelParameters(BigDecimal defaultTemperature, BigDecimal defaultTopP,
                              Integer defaultMaxOutputTokens) {

    public static final ModelParameters NONE = new ModelParameters(null, null, null);
}
