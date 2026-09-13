package com.example.stardust_springboot.ai;

import com.example.stardust_springboot.ai.attachment.ChatAttachment;
import com.example.stardust_springboot.ai.attachment.ChatAttachmentKind;
import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.entity.AiProvider;
import com.example.stardust_springboot.ai.entity.ModelType;
import com.example.stardust_springboot.ai.entity.ProviderType;
import com.example.stardust_springboot.ai.gateway.AiGateway;
import com.example.stardust_springboot.ai.gateway.AiGatewayEvent;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.gateway.StreamCancellation;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.repository.AiProviderRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end ownership of the chat attachment chain inside Spring: upload → {@code attachmentIds} →
 * persisted {@code chat_message_attachment} → content-bearing {@code AiGatewayRequest.attachments}.
 *
 * <p>The gateway is faked at the port boundary, so these tests prove what Spring would actually put
 * on the wire to the Python AI service. They deliberately do not assert anything about the model
 * output: that is the provider's job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ChatAttachmentIntegrationTests.TestGatewayConfiguration.class)
class ChatAttachmentIntegrationTests {
    private static final String PASSWORD = "StrongPassword!123";
    private static final String TEST_MARKER = "STARDUST_FILE_TEST_928374";
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiProviderRepository providerRepository;
    @Autowired
    private AiModelRepository modelRepository;
    @Autowired
    private FakeAttachmentGateway gateway;

    private String textModelId;
    private String visionModelId;

    @BeforeEach
    void setUpModels() {
        gateway.lastRequest.set(null);
        AiProvider provider = new AiProvider("attachment-provider-" + suffix(), "Attachment Provider",
                ProviderType.OPENAI_COMPATIBLE, "https://provider.test/v1", null);
        provider.enable();
        providerRepository.saveAndFlush(provider);

        AiModel textModel = new AiModel(provider, "text-model-" + suffix(), "external-text-model",
                "Text Chat Model", ModelType.CHAT);
        textModel.enable();
        textModelId = modelRepository.saveAndFlush(textModel).getPublicId();

        AiModel visionModel = new AiModel(provider, "vision-model-" + suffix(), "external-vision-model",
                "Vision Chat Model", ModelType.CHAT);
        visionModel.enable();
        visionModel.update("Vision Chat Model", "external-vision-model", ModelType.CHAT,
                "{\"streaming\":true,\"vision\":true}", null, null, null, null, null, null, 0);
        visionModelId = modelRepository.saveAndFlush(visionModel).getPublicId();
    }

    @Test
    void textAttachmentContentReachesTheAiGatewayAndStaysReferencedByTheMessage() throws Exception {
        String token = register("attachment-text");
        String conversationId = createConversation(token);
        String fileId = upload(token, "test.txt", "text/plain", TEST_MARKER.getBytes());

        stream(token, conversationId, "attachment-text-1", textModelId, fileId);

        AiGatewayRequest request = gateway.lastRequest.get();
        assertThat(request.attachments()).hasSize(1);
        ChatAttachment attachment = request.attachments().getFirst();
        assertThat(attachment.kind()).isEqualTo(ChatAttachmentKind.TEXT);
        assertThat(attachment.fileId()).isEqualTo(fileId);
        assertThat(attachment.fileName()).isEqualTo("test.txt");
        assertThat(attachment.mimeType()).isEqualTo("text/plain");
        assertThat(attachment.text()).contains(TEST_MARKER);
        assertThat(attachment.imageBase64()).isNull();
        assertThat(attachment.sizeBytes()).isEqualTo(TEST_MARKER.getBytes().length);

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains(fileId));
    }

    @Test
    void regenerateKeepsTheAttachmentsOfTheOriginalUserMessage() throws Exception {
        String token = register("attachment-regen");
        String conversationId = createConversation(token);
        String fileId = upload(token, "regen.txt", "text/plain", TEST_MARKER.getBytes());

        stream(token, conversationId, "attachment-regen-1", textModelId, fileId);
        String assistantId = JsonPath.read(messageHistory(token, conversationId), "$.data.items[1].id");

        gateway.lastRequest.set(null);
        mockMvc.perform(post("/api/v1/messages/{id}/regenerate", assistantId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "attachment-regen-2")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"" + textModelId + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn().getAsyncResult(5000);

        assertThat(gateway.lastRequest.get().attachments()).hasSize(1);
        assertThat(gateway.lastRequest.get().attachments().getFirst().fileId()).isEqualTo(fileId);
        assertThat(gateway.lastRequest.get().attachments().getFirst().text()).contains(TEST_MARKER);
    }

    @Test
    void imageAttachmentIsDeliveredAsBase64OnlyForVisionModels() throws Exception {
        String token = register("attachment-image");
        String visionConversation = createConversation(token);
        String fileId = upload(token, "pixel.png", "image/png", PNG_BYTES);

        stream(token, visionConversation, "attachment-image-1", visionModelId, fileId);
        ChatAttachment image = gateway.lastRequest.get().attachments().getFirst();
        assertThat(image.kind()).isEqualTo(ChatAttachmentKind.IMAGE);
        assertThat(image.imageBase64()).isNotBlank();
        assertThat(image.text()).isNull();

        String plainConversation = createConversation(token);
        stream(token, plainConversation, "attachment-image-2", textModelId, fileId);
        ChatAttachment skipped = gateway.lastRequest.get().attachments().getFirst();
        assertThat(skipped.kind()).isEqualTo(ChatAttachmentKind.UNSUPPORTED);
        assertThat(skipped.imageBase64()).isNull();
        assertThat(skipped.note()).contains("image input");
    }

    @Test
    void pdfTextAttachmentIsExtractedAndReachesTheAiGatewayAsText() throws Exception {
        String token = register("attachment-pdf-text");
        String conversationId = createConversation(token);
        byte[] pdf = buildPdfWithText(TEST_MARKER);
        String fileId = upload(token, "paper.pdf", "application/pdf", pdf);

        stream(token, conversationId, "attachment-pdf-text-1", textModelId, fileId);

        ChatAttachment attachment = gateway.lastRequest.get().attachments().getFirst();
        assertThat(attachment.kind()).isEqualTo(ChatAttachmentKind.TEXT);
        assertThat(attachment.fileId()).isEqualTo(fileId);
        assertThat(attachment.fileName()).isEqualTo("paper.pdf");
        assertThat(attachment.mimeType()).isEqualTo("application/pdf");
        assertThat(attachment.text()).contains(TEST_MARKER);
        assertThat(attachment.imageBase64()).isNull();
        assertThat(attachment.sizeBytes()).isEqualTo(pdf.length);
    }

    @Test
    void corruptOrImageOnlyPdfIsReportedUnsupportedInsteadOfSilentlyDropped() throws Exception {
        String token = register("attachment-pdf");
        String conversationId = createConversation(token);
        // A PDF that parses as malformed / has no text layer must not invent content.
        String fileId = upload(token, "doc.pdf", "application/pdf", "%PDF-1.4\n1 0 obj\n".getBytes());

        stream(token, conversationId, "attachment-pdf-1", textModelId, fileId);

        ChatAttachment attachment = gateway.lastRequest.get().attachments().getFirst();
        assertThat(attachment.kind()).isEqualTo(ChatAttachmentKind.UNSUPPORTED);
        assertThat(attachment.fileId()).isEqualTo(fileId);
        assertThat(attachment.note()).isNotBlank();
    }

    @Test
    void unknownOrForeignFileIdsAreRejectedBeforeAnyContentIsRead() throws Exception {
        String owner = register("attachment-owner-2");
        String attacker = register("attachment-attacker-2");
        String ownerConversation = createConversation(owner);
        String attackerConversation = createConversation(attacker);
        String fileId = upload(owner, "owned.txt", "text/plain", TEST_MARKER.getBytes());

        mockMvc.perform(post("/api/v1/conversations/{id}/messages/stream", ownerConversation)
                        .header("Authorization", bearer(owner))
                        .header("Idempotency-Key", "attachment-missing-1")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\",\"modelId\":\"" + textModelId
                                + "\",\"attachmentIds\":[\"00000000000000000000000000\"]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/conversations/{id}/messages/stream", attackerConversation)
                        .header("Authorization", bearer(attacker))
                        .header("Idempotency-Key", "attachment-foreign-1")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"steal\",\"modelId\":\"" + textModelId
                                + "\",\"attachmentIds\":[\"" + fileId + "\"]}"))
                .andExpect(status().isNotFound());

        assertThat(gateway.lastRequest.get()).isNull();
    }

    private void stream(String token, String conversationId, String idempotencyKey, String modelId,
                        String fileId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{id}/messages/stream", conversationId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"这个文件中的测试字符串是什么？","contentType":"PLAIN_TEXT","modelId":"%s",
                                 "attachmentIds":["%s"]}
                                """.formatted(modelId, fileId)))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private String messageHistory(String token, String conversationId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String upload(String token, String filename, String mime, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", filename, mime, content))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Attachment tester"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String createConversation(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Attachment test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static byte[] buildPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    static final class FakeAttachmentGateway implements AiGateway {
        final AtomicReference<AiGatewayRequest> lastRequest = new AtomicReference<>();

        @Override
        public void stream(AiGatewayRequest request, StreamCancellation cancellation,
                           Consumer<AiGatewayEvent> consumer) {
            lastRequest.set(request);
            consumer.accept(new AiGatewayEvent("start", Map.of()));
            consumer.accept(new AiGatewayEvent("delta", Map.of("content", "已读取附件")));
            consumer.accept(new AiGatewayEvent("usage", Map.of(
                    "promptTokens", 5, "completionTokens", 2, "totalTokens", 7)));
            consumer.accept(new AiGatewayEvent("done", Map.of("finishReason", "stop")));
        }
    }

    @TestConfiguration
    static class TestGatewayConfiguration {
        @Bean
        @Primary
        FakeAttachmentGateway fakeAttachmentGateway() {
            return new FakeAttachmentGateway();
        }
    }
}
