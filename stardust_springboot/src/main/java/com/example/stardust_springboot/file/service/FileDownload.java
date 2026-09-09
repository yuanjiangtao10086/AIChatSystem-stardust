package com.example.stardust_springboot.file.service;

import java.io.InputStream;

public record FileDownload(String name, String mimeType, long size, InputStream input) {
}
