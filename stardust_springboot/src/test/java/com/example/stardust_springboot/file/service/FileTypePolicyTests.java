package com.example.stardust_springboot.file.service;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypePolicyTests {
    private final FileTypePolicy policy = new FileTypePolicy(new StorageProperties(
            "local", Path.of("./data/files"), 25L * 1024 * 1024, 1024L * 1024 * 1024, 10L * 1024 * 1024));

    @Test
    void acceptsAllowedArtifactExtensionsAndSniffsMime() {
        ValidatedUpload py = policy.validateGenerated("demo.py", "text/x-python", "print(1)\n".getBytes());
        assertThat(py.extension()).isEqualTo("py");
        assertThat(py.detectedMime()).isEqualTo("text/x-python");

        ValidatedUpload sql = policy.validateGenerated("q.sql", "application/sql", "select 1;".getBytes());
        assertThat(sql.extension()).isEqualTo("sql");

        ValidatedUpload html = policy.validateGenerated("p.html", "text/html", "<p>hi</p>".getBytes());
        assertThat(html.extension()).isEqualTo("html");

        ValidatedUpload yaml = policy.validateGenerated("c.yaml", "text/yaml", "a: 1\n".getBytes());
        assertThat(yaml.extension()).isEqualTo("yaml");
    }

    @Test
    void rejectsDisallowedArtifactType() {
        assertThatThrownBy(() -> policy.validateGenerated("x.exe", "application/x-msdownload", "MZ".getBytes()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ARTIFACT_TYPE_NOT_ALLOWED);
    }

    @Test
    void rejectsOversizeArtifact() {
        byte[] big = new byte[11 * 1024 * 1024];
        assertThatThrownBy(() -> policy.validateGenerated("big.txt", "text/plain", big))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ARTIFACT_TOO_LARGE);
    }

    @Test
    void rejectsForgedMimeDisguisedAsTextArtifact() {
        // A PNG binary header must not pass as a .py text artifact.
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n'};
        assertThatThrownBy(() -> policy.validateGenerated("evil.py", "text/x-python", png))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ARTIFACT_TYPE_NOT_ALLOWED);
    }
}
