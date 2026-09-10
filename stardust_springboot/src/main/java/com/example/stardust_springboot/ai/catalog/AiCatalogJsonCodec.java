package com.example.stardust_springboot.ai.catalog;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts the JSON columns of the AI catalog to and from typed value objects.
 *
 * <p>Reading is intentionally tolerant: earlier phases stored capabilities as a string array
 * ({@code ["streaming","reasoning"]}) and parameters in a nested form
 * ({@code {"temperature":{"min":0,"max":2,"default":0.7}}}). Those rows stay readable instead of
 * forcing a data rewrite, and a corrupt value degrades to "not configured" rather than failing the
 * whole console page.
 */
@Component
public class AiCatalogJsonCodec {

    private final ObjectMapper mapper;

    public AiCatalogJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ModelCapabilities readCapabilities(String json) {
        JsonNode root = read(json);
        if (root == null) {
            return ModelCapabilities.NONE;
        }
        if (root.isArray()) {
            Set<String> names = new HashSet<>();
            root.forEach(node -> names.add(node.asText("").trim().toLowerCase(Locale.ROOT)));
            return new ModelCapabilities(names.contains("streaming"), names.contains("vision"),
                    names.contains("reasoning"), names.contains("embedding"));
        }
        return new ModelCapabilities(flag(root, "streaming"), flag(root, "vision"),
                flag(root, "reasoning"), flag(root, "embedding"));
    }

    public String writeCapabilities(ModelCapabilities capabilities) {
        ModelCapabilities value = capabilities == null ? ModelCapabilities.NONE : capabilities;
        ObjectNode node = mapper.createObjectNode();
        node.put("streaming", value.streaming());
        node.put("vision", value.vision());
        node.put("reasoning", value.reasoning());
        node.put("embedding", value.embedding());
        return node.toString();
    }

    public ModelParameters readParameters(String json) {
        JsonNode root = read(json);
        if (root == null || !root.isObject()) {
            return ModelParameters.NONE;
        }
        return new ModelParameters(
                decimal(root, "defaultTemperature", "temperature"),
                decimal(root, "defaultTopP", "topP"),
                integer(root, "defaultMaxOutputTokens", "maxOutputTokens"));
    }

    public String writeParameters(ModelParameters parameters) {
        ObjectNode node = mapper.createObjectNode();
        if (parameters != null) {
            if (parameters.defaultTemperature() != null) {
                node.put("defaultTemperature", parameters.defaultTemperature());
            }
            if (parameters.defaultTopP() != null) {
                node.put("defaultTopP", parameters.defaultTopP());
            }
            if (parameters.defaultMaxOutputTokens() != null) {
                node.put("defaultMaxOutputTokens", parameters.defaultMaxOutputTokens());
            }
        }
        return node.toString();
    }

    public ProviderConfig readProviderConfig(String json) {
        JsonNode root = read(json);
        if (root == null || !root.isObject()) {
            return ProviderConfig.NONE;
        }
        return new ProviderConfig(integer(root, "timeoutSeconds", null),
                integer(root, "connectTimeoutSeconds", null));
    }

    public String writeProviderConfig(ProviderConfig config) {
        ObjectNode node = mapper.createObjectNode();
        if (config != null) {
            if (config.timeoutSeconds() != null) {
                node.put("timeoutSeconds", config.timeoutSeconds());
            }
            if (config.connectTimeoutSeconds() != null) {
                node.put("connectTimeoutSeconds", config.connectTimeoutSeconds());
            }
        }
        return node.toString();
    }

    /**
     * Builds audit metadata. Callers must only pass non-secret values: credential references and
     * API keys must never be routed through this method (ADR-016).
     */
    public String writeMetadata(Map<String, ?> values) {
        ObjectNode node = mapper.createObjectNode();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key == null || value == null) {
                    return;
                }
                if (value instanceof Boolean flag) {
                    node.put(key, flag);
                } else if (value instanceof Integer number) {
                    node.put(key, number);
                } else if (value instanceof Long number) {
                    node.put(key, number);
                } else if (value instanceof BigDecimal number) {
                    node.put(key, number);
                } else {
                    node.put(key, String.valueOf(value));
                }
            });
        }
        return node.toString();
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(json);
            return root == null || root.isNull() ? null : root;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean flag(JsonNode root, String name) {
        return root.path(name).asBoolean(false);
    }

    /** Reads {@code {"new":"..."}} first, then the legacy {@code {"legacy":{"default":"..."}}} form. */
    private BigDecimal decimal(JsonNode root, String name, String legacyName) {
        JsonNode direct = root.path(name);
        if (direct.isNumber() || direct.isTextual()) {
            return direct.decimalValue();
        }
        if (legacyName == null) {
            return null;
        }
        JsonNode nested = root.path(legacyName).path("default");
        return nested.isNumber() || nested.isTextual() ? nested.decimalValue() : null;
    }

    private Integer integer(JsonNode root, String name, String legacyName) {
        JsonNode direct = root.path(name);
        if (direct.isInt() || direct.isLong() || direct.isTextual()) {
            return direct.asInt();
        }
        if (legacyName == null) {
            return null;
        }
        JsonNode nested = root.path(legacyName).path("default");
        return nested.isNumber() || nested.isTextual() ? nested.asInt() : null;
    }
}
