package com.example.stardust_springboot.file.storage;

import com.example.stardust_springboot.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

@Component
public class LocalStorageProvider implements StorageProvider {
    private static final Pattern SAFE_KEY = Pattern.compile(
            "[0-9A-HJKMNP-TV-Z]{26}/[0-9]{4}/[0-9]{2}/[0-9A-HJKMNP-TV-Z]{26}\\.[a-z0-9]{1,16}");

    private final Path root;

    public LocalStorageProvider(StorageProperties properties) {
        this.root = properties.localRoot().toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public String key() {
        return "local";
    }

    @Override
    public void put(String objectKey, InputStream input) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(input, staging, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return Files.newInputStream(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || !SAFE_KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("Invalid storage object key");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage object escaped configured root");
        }
        return resolved;
    }
}
