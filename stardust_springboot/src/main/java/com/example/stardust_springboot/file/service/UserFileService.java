package com.example.stardust_springboot.file.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.file.dto.FileView;
import com.example.stardust_springboot.file.dto.StorageUsageView;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.file.repository.UserStorageUsageRepository;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.knowledge.repository.KnowledgeDocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.ZoneOffset;

@Service
public class UserFileService {
    private final UserFileRepository fileRepository;
    private final UserStorageUsageRepository usageRepository;
    private final FilePersistenceService persistence;
    private final FileTypePolicy typePolicy;
    private final StorageService storage;
    private final Clock clock;
    private final KnowledgeDocumentRepository knowledgeDocuments;

    public UserFileService(UserFileRepository fileRepository,
                           UserStorageUsageRepository usageRepository,
                           FilePersistenceService persistence,
                           FileTypePolicy typePolicy,
                           StorageService storage,
                           Clock clock,
                           KnowledgeDocumentRepository knowledgeDocuments) {
        this.fileRepository = fileRepository;
        this.usageRepository = usageRepository;
        this.persistence = persistence;
        this.typePolicy = typePolicy;
        this.storage = storage;
        this.clock = clock;
        this.knowledgeDocuments = knowledgeDocuments;
    }

    public FileView upload(AuthenticatedUser principal, MultipartFile multipart) {
        ValidatedUpload upload = typePolicy.validate(multipart);
        String storageName = PublicIdGenerator.newUlid() + "." + upload.extension();
        var date = clock.instant().atZone(ZoneOffset.UTC);
        String objectKey = principal.publicId() + "/" + date.getYear() + "/"
                + "%02d".formatted(date.getMonthValue()) + "/" + storageName;
        UserFile pending = persistence.reserve(principal.id(), upload, storageName,
                objectKey, storage.providerKey());
        try (var input = multipart.getInputStream()) {
            storage.put(objectKey, input);
            return FileView.from(persistence.complete(principal.id(), pending.getPublicId()));
        } catch (Exception exception) {
            try {
                storage.delete(objectKey);
            } catch (IOException ignored) {
                // The failed row remains diagnosable; orphan reconciliation is an operations concern.
            }
            persistence.failUpload(principal.id(), pending.getPublicId());
            if (exception instanceof BusinessException business) throw business;
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<FileView> list(AuthenticatedUser principal, int page, int size, String search) {
        String normalized = normalizeSearch(search);
        return PageResult.from(fileRepository.findOwnedAvailable(principal.id(), normalized,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")))).map(FileView::from));
    }

    @Transactional(readOnly = true)
    public FileView get(AuthenticatedUser principal, String fileId) {
        return FileView.from(requireAvailable(principal.id(), fileId));
    }

    @Transactional(readOnly = true)
    public StorageUsageView usage(AuthenticatedUser principal) {
        return usageRepository.findById(principal.id()).map(StorageUsageView::from)
                .orElseThrow(() -> new IllegalStateException("Storage usage row is missing"));
    }

    public FileView rename(AuthenticatedUser principal, String fileId, String requestedName) {
        UserFile current = requireAvailable(principal.id(), fileId);
        String safeName = typePolicy.normalizeRename(requestedName, current.getExtension());
        return FileView.from(persistence.rename(principal.id(), fileId, safeName));
    }

    public FileDownload download(AuthenticatedUser principal, String fileId, boolean preview) {
        UserFile file = requireAvailable(principal.id(), fileId);
        if (preview && !file.getDetectedMime().startsWith("image/")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        try {
            return new FileDownload(file.getOriginalName(), file.getDetectedMime(),
                    file.getSizeBytes(), storage.open(file.getObjectKey()));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    public void delete(AuthenticatedUser principal, String fileId) {
        UserFile owned = requireAvailable(principal.id(), fileId);
        if (knowledgeDocuments.existsByUserFileIdAndUserIdAndDeletedAtIsNull(
                owned.getId(), principal.id())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT,
                    "delete the knowledge document before deleting its source file");
        }
        UserFile file = persistence.beginDelete(principal.id(), fileId);
        try {
            storage.delete(file.getObjectKey());
            persistence.finishDelete(principal.id(), fileId);
        } catch (Exception exception) {
            persistence.restoreDelete(principal.id(), fileId);
            if (exception instanceof BusinessException business) throw business;
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    private UserFile requireAvailable(long userId, String fileId) {
        return fileRepository.findByPublicIdAndUserIdAndStatusAndDeletedAtIsNull(
                        fileId, userId, UserFileStatus.AVAILABLE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String normalizeSearch(String value) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
