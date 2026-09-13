package com.example.stardust_springboot.ai.attachment;

import com.example.stardust_springboot.ai.catalog.AiCatalogJsonCodec;
import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.config.AttachmentProperties;
import com.example.stardust_springboot.conversation.entity.ChatMessageAttachment;
import com.example.stardust_springboot.conversation.repository.ChatMessageAttachmentRepository;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Turns the persisted {@code chat_message_attachment} references of a message into AI input.
 *
 * <p>Spring owns this step because it owns file metadata, storage access and the model capability
 * catalog. The result is a content-bearing projection: the AI service never receives an object key
 * or a local path, and a remote LLM provider could not resolve either.
 *
 * <p>Failures are never fatal for the chat: a file that cannot be read, exceeds a limit or has no
 * extractor is reported as {@link ChatAttachmentKind#UNSUPPORTED} with an explicit note, so the model
 * tells the user the attachment was not readable instead of inventing an answer about it.
 */
@Service
public class ChatAttachmentResolver {
    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentResolver.class);
    private static final int MAX_ATTACHMENTS = 10;

    private final ChatMessageAttachmentRepository attachmentRepository;
    private final StorageService storage;
    private final AiCatalogJsonCodec catalog;
    private final AttachmentProperties properties;
    private final PdfTextExtractor pdfTextExtractor;

    public ChatAttachmentResolver(ChatMessageAttachmentRepository attachmentRepository,
                                  StorageService storage, AiCatalogJsonCodec catalog,
                                  AttachmentProperties properties, PdfTextExtractor pdfTextExtractor) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.catalog = catalog;
        this.properties = properties;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    @Transactional(readOnly = true)
    public List<ChatAttachment> resolve(Long messageId, AiModel model, String requestId) {
        List<ChatMessageAttachment> links = attachmentRepository.findByMessage(messageId);
        if (links.isEmpty()) {
            return List.of();
        }
        boolean vision = catalog.readCapabilities(model.getCapabilitiesJson()).vision();
        long remainingBytes = properties.maxTotalBytes();
        List<ChatAttachment> resolved = new ArrayList<>(links.size());
        for (ChatMessageAttachment link : links) {
            if (resolved.size() >= MAX_ATTACHMENTS) {
                break;
            }
            UserFile file = link.getUserFile();
            String fileId = file.getPublicId();
            String name = file.getOriginalName();
            String mime = lower(file.getDetectedMime());
            long size = file.getSizeBytes();

            if (size > properties.maxFileBytes() || size > remainingBytes) {
                resolved.add(ChatAttachment.unsupported(fileId, name, mime, size,
                        "file exceeds the chat attachment size limit"));
                continue;
            }
            byte[] content;
            try {
                content = read(file);
            } catch (IOException error) {
                log.warn("Attachment unreadable requestId={} fileId={} mime={}", requestId, fileId, mime, error);
                resolved.add(ChatAttachment.unsupported(fileId, name, mime, size,
                        "file content could not be read from storage"));
                continue;
            }
            if (properties.imageMimeTypes().contains(mime)) {
                if (!vision) {
                    resolved.add(ChatAttachment.unsupported(fileId, name, mime, size,
                            "the selected model does not accept image input"));
                    continue;
                }
                remainingBytes -= content.length;
                resolved.add(ChatAttachment.image(fileId, name, mime, content.length,
                        Base64.getEncoder().encodeToString(content)));
            } else if (properties.pdfMimeTypes().contains(mime)) {
                String extracted = pdfTextExtractor.extract(content, requestId, properties.maxTextChars());
                if (extracted == null || extracted.isBlank()) {
                    resolved.add(ChatAttachment.unsupported(fileId, name, mime, size,
                            "the PDF could not be parsed or has no extractable text layer"
                                    + " (scanned-image PDFs are not supported)"));
                    continue;
                }
                String note = null;
                String text = extracted;
                if (text.length() > properties.maxTextChars()) {
                    text = safeHead(text, properties.maxTextChars());
                    note = "content truncated to the attachment text limit";
                }
                remainingBytes -= content.length;
                resolved.add(ChatAttachment.text(fileId, name, mime, content.length, text, note));
            } else if (properties.textMimeTypes().contains(mime)) {
                String text = new String(content, StandardCharsets.UTF_8);
                String note = null;
                if (text.length() > properties.maxTextChars()) {
                    text = safeHead(text, properties.maxTextChars());
                    note = "content truncated to the attachment text limit";
                }
                remainingBytes -= content.length;
                resolved.add(ChatAttachment.text(fileId, name, mime, content.length, text, note));
            } else {
                resolved.add(ChatAttachment.unsupported(fileId, name, mime, size,
                        "no text or image extractor is configured for this file type"));
            }
        }
        for (ChatAttachment attachment : resolved) {
            log.debug("AI attachment requestId={} messageId={} fileId={} mime={} size={} kind={}",
                    requestId, messageId, attachment.fileId(), attachment.mimeType(),
                    attachment.sizeBytes(), attachment.kind());
        }
        log.info("AI attachments resolved requestId={} messageId={} model={} attachmentCount={} vision={}",
                requestId, messageId, model.getExternalModelId(), resolved.size(), vision);
        return List.copyOf(resolved);
    }

    private byte[] read(UserFile file) throws IOException {
        // readNBytes (not readAllBytes) keeps the worst case bounded even if the stored object ever
        // disagrees with the recorded size.
        try (InputStream input = storage.open(file.getObjectKey())) {
            return input.readNBytes(Math.toIntExact(properties.maxFileBytes()));
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safeHead(String value, int length) {
        String head = value.substring(0, length);
        if (!head.isEmpty() && Character.isHighSurrogate(head.charAt(head.length() - 1))) {
            return head.substring(0, head.length() - 1);
        }
        return head;
    }
}
