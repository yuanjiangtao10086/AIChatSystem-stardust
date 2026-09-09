package com.example.stardust_springboot.file.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.file.dto.FileView;
import com.example.stardust_springboot.file.dto.RenameFileRequest;
import com.example.stardust_springboot.file.dto.StorageUsageView;
import com.example.stardust_springboot.file.service.FileDownload;
import com.example.stardust_springboot.file.service.UserFileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/v1/files")
public class UserFileController {
    private final UserFileService fileService;

    public UserFileController(UserFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<FileView>> upload(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(fileService.upload(principal, file)));
    }

    @GetMapping
    public ApiResult<PageResult<FileView>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 255) String search) {
        return ApiResult.success(fileService.list(principal, page, size, search));
    }

    @GetMapping("/usage")
    public ApiResult<StorageUsageView> usage(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResult.success(fileService.usage(principal));
    }

    @GetMapping("/{fileId}")
    public ApiResult<FileView> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable String fileId) {
        return ApiResult.success(fileService.get(principal, fileId));
    }

    @PatchMapping("/{fileId}")
    public ApiResult<FileView> rename(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @PathVariable String fileId,
                                      @Valid @RequestBody RenameFileRequest request) {
        return ApiResult.success(fileService.rename(principal, fileId, request.name()));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable String fileId) {
        return content(fileService.download(principal, fileId, false), false);
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<InputStreamResource> preview(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable String fileId) {
        return content(fileService.download(principal, fileId, true), true);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable String fileId) {
        fileService.delete(principal, fileId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<InputStreamResource> content(FileDownload file, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(file.input()));
    }
}
