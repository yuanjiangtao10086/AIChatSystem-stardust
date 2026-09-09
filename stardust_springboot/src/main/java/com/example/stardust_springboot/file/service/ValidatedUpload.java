package com.example.stardust_springboot.file.service;

public record ValidatedUpload(String name, String extension, String declaredMime,
                              String detectedMime, long size, String sha256, String metadataJson) {
}
