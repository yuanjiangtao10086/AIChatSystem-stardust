package com.example.stardust_springboot.file.storage;

import java.io.IOException;
import java.io.InputStream;

public interface StorageProvider {
    String key();

    void put(String objectKey, InputStream input) throws IOException;

    InputStream open(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;
}
