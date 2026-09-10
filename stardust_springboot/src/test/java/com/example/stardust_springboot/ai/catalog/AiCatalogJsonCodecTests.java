package com.example.stardust_springboot.ai.catalog;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11E: the AI catalog JSON columns must stay readable across schema shape changes.
 *
 * Rows written by earlier phases use a capability array and a nested parameter policy; the codec
 * reads both and degrades corrupt values to "not configured" instead of failing a console page.
 */
class AiCatalogJsonCodecTests {

    private final AiCatalogJsonCodec codec = new AiCatalogJsonCodec(new ObjectMapper());

    @Test
    void readsLegacyCapabilityArray() {
        ModelCapabilities capabilities = codec.readCapabilities("[\"streaming\",\"reasoning\"]");

        assertThat(capabilities.streaming()).isTrue();
        assertThat(capabilities.reasoning()).isTrue();
        assertThat(capabilities.vision()).isFalse();
        assertThat(capabilities.embedding()).isFalse();
    }

    @Test
    void readsTypedCapabilityObject() {
        ModelCapabilities capabilities = codec.readCapabilities(
                "{\"streaming\":false,\"vision\":true,\"reasoning\":false,\"embedding\":true}");

        assertThat(capabilities.streaming()).isFalse();
        assertThat(capabilities.vision()).isTrue();
        assertThat(capabilities.embedding()).isTrue();
    }

    @Test
    void corruptOrMissingCapabilitiesDegradeToNone() {
        assertThat(codec.readCapabilities("{not-json")).isEqualTo(ModelCapabilities.NONE);
        assertThat(codec.readCapabilities(null)).isEqualTo(ModelCapabilities.NONE);
        assertThat(codec.readCapabilities("")).isEqualTo(ModelCapabilities.NONE);
    }

    @Test
    void writesCapabilitiesAsTypedObject() {
        String json = codec.writeCapabilities(new ModelCapabilities(true, false, false, true));

        assertThat(json).contains("\"streaming\":true").contains("\"vision\":false")
                .contains("\"reasoning\":false").contains("\"embedding\":true");
    }

    @Test
    void readsLegacyNestedParameterPolicy() {
        ModelParameters parameters = codec.readParameters("{\"temperature\":{\"min\":0,\"max\":2,\"default\":0.7}}");

        assertThat(parameters.defaultTemperature()).isEqualByComparingTo(new BigDecimal("0.7"));
        assertThat(parameters.defaultTopP()).isNull();
        assertThat(parameters.defaultMaxOutputTokens()).isNull();
    }

    @Test
    void roundTripsTypedParameters() {
        ModelParameters written = codec.readParameters(codec.writeParameters(
                new ModelParameters(new BigDecimal("1.20"), new BigDecimal("0.90"), 2048)));

        assertThat(written.defaultTemperature()).isEqualByComparingTo(new BigDecimal("1.2"));
        assertThat(written.defaultTopP()).isEqualByComparingTo(new BigDecimal("0.9"));
        assertThat(written.defaultMaxOutputTokens()).isEqualTo(2048);
        assertThat(codec.writeParameters(ModelParameters.NONE)).isEqualTo("{}");
    }

    @Test
    void readsProviderTimeoutsAndKeepsUnknownKeysOut() {
        ProviderConfig config = codec.readProviderConfig("{\"connectTimeoutSeconds\":10,\"legacy\":true}");

        assertThat(config.connectTimeoutSeconds()).isEqualTo(10);
        assertThat(config.timeoutSeconds()).isNull();
        assertThat(codec.readProviderConfig("[]")).isEqualTo(ProviderConfig.NONE);
    }
}
