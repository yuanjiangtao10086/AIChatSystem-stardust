package com.example.stardust_springboot.file.storage;

import com.example.stardust_springboot.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StorageService {
    private final StorageProvider activeProvider;

    public StorageService(List<StorageProvider> providers, StorageProperties properties) {
        Map<String, StorageProvider> registry = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.key().toLowerCase(Locale.ROOT), Function.identity()));
        String selected = properties.provider().toLowerCase(Locale.ROOT);
        this.activeProvider = java.util.Optional.ofNullable(registry.get(selected))
                .orElseThrow(() -> new IllegalStateException("Unknown storage provider: " + selected));
    }

    public String providerKey() { return activeProvider.key(); }
    public void put(String objectKey, InputStream input) throws IOException { activeProvider.put(objectKey, input); }
    public InputStream open(String objectKey) throws IOException { return activeProvider.open(objectKey); }
    public void delete(String objectKey) throws IOException { activeProvider.delete(objectKey); }
}
