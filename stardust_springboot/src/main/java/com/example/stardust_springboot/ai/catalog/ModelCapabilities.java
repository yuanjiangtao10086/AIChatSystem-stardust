package com.example.stardust_springboot.ai.catalog;

/**
 * Typed capabilities of an AI model, persisted as {@code ai_model.capabilities_json}.
 *
 * <p>Capabilities are descriptive metadata: they are what the console and the model picker show,
 * they never grant access by themselves. {@code embedding} is meaningful for models that can be
 * used by the RAG pipeline; {@code streaming/vision/reasoning} describe the chat surface.
 */
public record ModelCapabilities(boolean streaming, boolean vision, boolean reasoning, boolean embedding) {

    public static final ModelCapabilities NONE = new ModelCapabilities(false, false, false, false);
}
