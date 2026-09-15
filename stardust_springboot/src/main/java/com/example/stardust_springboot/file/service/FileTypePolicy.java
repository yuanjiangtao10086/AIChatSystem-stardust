package com.example.stardust_springboot.file.service;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class FileTypePolicy {
    private static final int SNIFF_BYTES = 8192;
    private static final Map<String, Set<String>> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("md", Set.of("text/markdown", "text/plain")),
            Map.entry("csv", Set.of("text/csv", "text/plain", "application/vnd.ms-excel")),
            Map.entry("json", Set.of("application/json", "text/json", "text/plain")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("py", Set.of("text/x-python")),
            Map.entry("html", Set.of("text/html")),
            Map.entry("htm", Set.of("text/html")),
            Map.entry("sql", Set.of("application/sql", "text/x-sql")),
            Map.entry("yaml", Set.of("text/yaml", "application/yaml")),
            Map.entry("yml", Set.of("text/yaml", "application/yaml")),
            Map.entry("xml", Set.of("application/xml", "text/xml")));

    private final long maxFileBytes;
    private final long artifactMaxBytes;

    public FileTypePolicy(StorageProperties properties) {
        this.maxFileBytes = properties.maxFileBytes();
        this.artifactMaxBytes = properties.artifactMaxBytes();
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (file.getSize() > maxFileBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String name = normalizeName(file.getOriginalFilename());
        String extension = extension(name);
        String declared = normalizeMime(file.getContentType());
        Set<String> allowedMimes = MIME_BY_EXTENSION.get(extension);
        if (allowedMimes == null || !allowedMimes.contains(declared)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        try {
            byte[] header;
            try (InputStream input = file.getInputStream()) {
                header = input.readNBytes(SNIFF_BYTES);
            }
            String detected = detect(extension, header, declared);
            String sha256 = digest(file);
            String category = detected.startsWith("image/") ? "image" : "document";
            return new ValidatedUpload(name, extension, declared, detected, file.getSize(), sha256,
                    "{\"category\":\"" + category + "\",\"previewable\":"
                            + detected.startsWith("image/") + "}");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    public String normalizeRename(String value, String requiredExtension) {
        String name = normalizeName(value);
        if (!extension(name).equals(requiredExtension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return name;
    }

    /** Validates an AI-generated artifact supplied by the Python service as raw bytes. */
    public ValidatedUpload validateGenerated(String originalName, String declaredMime, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (content.length > artifactMaxBytes) {
            throw new BusinessException(ErrorCode.ARTIFACT_TOO_LARGE);
        }
        String name = normalizeName(originalName);
        String ext = extension(name);
        String declared = normalizeMime(declaredMime);
        Set<String> allowedMimes = MIME_BY_EXTENSION.get(ext);
        if (allowedMimes == null || !allowedMimes.contains(declared)) {
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_NOT_ALLOWED);
        }
        // Re-sniff the bytes so a forged MIME / extension cannot smuggle a disallowed payload in.
        // For text types the content must be valid UTF-8; for office types it must carry the OOXML
        // magic header. A mismatch means the declared type is not what the bytes actually are.
        String detected = detect(ext, content, declared);
        if (!detected.equals(declared)) {
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_NOT_ALLOWED);
        }
        String sha256 = digestBytes(content);
        return new ValidatedUpload(name, ext, declared, detected, content.length, sha256,
                "{\"category\":\"document\",\"previewable\":false,\"source\":\"ai\"}");
    }

    private String normalizeName(String raw) {
        if (raw == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        String leaf = raw.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        leaf = Normalizer.normalize(leaf, Normalizer.Form.NFKC).trim();
        StringBuilder safe = new StringBuilder();
        leaf.codePoints().filter(value -> !Character.isISOControl(value)).forEach(safe::appendCodePoint);
        String result = safe.toString().trim();
        if (result.isBlank() || result.equals(".") || result.equals("..") || result.length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return result;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String value) {
        if (value == null) return "application/octet-stream";
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String detect(String extension, byte[] bytes, String declared) {
        boolean valid = switch (extension) {
            case "png" -> starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "jpg", "jpeg" -> starts(bytes, 0xff, 0xd8, 0xff);
            case "gif" -> ascii(bytes, "GIF87a") || ascii(bytes, "GIF89a");
            case "webp" -> bytes.length >= 12 && ascii(bytes, "RIFF") && asciiAt(bytes, 8, "WEBP");
            case "pdf" -> ascii(bytes, "%PDF-");
            case "docx", "xlsx", "pptx" -> starts(bytes, 0x50, 0x4b, 0x03, 0x04);
            case "txt", "md", "csv", "json", "py", "html", "sql", "yaml", "xml", "htm", "yml" -> isUtf8Text(bytes);
            default -> false;
        };
        if (!valid) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        return declared;
    }

    private String digest(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                input.transferTo(OutputStreamSink.INSTANCE);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String digestBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ignored) {
            return false;
        }
    }

    private boolean starts(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++)
            if ((bytes[index] & 0xff) != expected[index]) return false;
        return true;
    }
    private boolean ascii(byte[] bytes, String value) { return asciiAt(bytes, 0, value); }
    private boolean asciiAt(byte[] bytes, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++)
            if (bytes[offset + index] != expected[index]) return false;
        return true;
    }

    private static final class OutputStreamSink extends java.io.OutputStream {
        private static final OutputStreamSink INSTANCE = new OutputStreamSink();
        @Override public void write(int value) { }
        @Override public void write(byte[] value, int offset, int length) { }
    }
}
