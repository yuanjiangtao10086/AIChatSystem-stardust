package com.example.stardust_springboot.file.service;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.StorageProperties;
import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.file.entity.UserStorageUsage;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.file.repository.UserStorageUsageRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.conversation.repository.ChatMessageAttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilePersistenceService {
    private final UserFileRepository fileRepository;
    private final UserStorageUsageRepository usageRepository;
    private final AppUserRepository userRepository;
    private final ChatMessageAttachmentRepository attachmentRepository;
    private final long defaultQuotaBytes;

    public FilePersistenceService(UserFileRepository fileRepository,
                                  UserStorageUsageRepository usageRepository,
                                  AppUserRepository userRepository,
                                  ChatMessageAttachmentRepository attachmentRepository,
                                  StorageProperties properties) {
        this.fileRepository = fileRepository;
        this.usageRepository = usageRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.defaultQuotaBytes = properties.defaultQuotaBytes();
    }

    @Transactional
    public UserFile reserve(long userId, ValidatedUpload upload, String storageName,
                            String objectKey, String provider) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        UserStorageUsage usage = usageRepository.findForUpdate(userId)
                .orElseGet(() -> usageRepository.saveAndFlush(new UserStorageUsage(user, defaultQuotaBytes)));
        if (!usage.canReserve(upload.size())) {
            throw new BusinessException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }
        long size = upload.size();
        usage.reserve(size);
        usageRepository.save(usage);
        return fileRepository.saveAndFlush(new UserFile(user, upload.name(), storageName, objectKey,
                upload.declaredMime(), upload.detectedMime(), upload.extension(), size,
                upload.sha256(), provider, upload.metadataJson()));
    }

    @Transactional
    public UserFile complete(long userId, String fileId) {
        UserStorageUsage usage = requireUsageForUpdate(userId);
        UserFile file = requireOwnedForUpdate(fileId, userId);
        if (file.getStatus() != UserFileStatus.UPLOADING) throw conflict();
        file.markAvailable();
        usage.complete(file.getSizeBytes());
        usageRepository.save(usage);
        return fileRepository.saveAndFlush(file);
    }

    @Transactional
    public void failUpload(long userId, String fileId) {
        UserStorageUsage usage = requireUsageForUpdate(userId);
        UserFile file = requireOwnedForUpdate(fileId, userId);
        if (file.getStatus() == UserFileStatus.UPLOADING) {
            file.markFailed();
            usage.release(file.getSizeBytes());
        }
    }

    @Transactional
    public UserFile beginDelete(long userId, String fileId) {
        UserFile file = requireOwnedForUpdate(fileId, userId);
        if (file.getStatus() != UserFileStatus.AVAILABLE) throw conflict();
        if (attachmentRepository.existsByUserFileId(file.getId())) throw conflict();
        file.beginDelete();
        return file;
    }

    @Transactional
    public void finishDelete(long userId, String fileId) {
        UserStorageUsage usage = requireUsageForUpdate(userId);
        UserFile file = requireOwnedForUpdate(fileId, userId);
        if (file.getStatus() != UserFileStatus.DELETING) throw conflict();
        file.softDelete();
        usage.remove(file.getSizeBytes());
    }

    @Transactional
    public void restoreDelete(long userId, String fileId) {
        UserFile file = requireOwnedForUpdate(fileId, userId);
        if (file.getStatus() == UserFileStatus.DELETING) file.restoreAvailable();
    }

    @Transactional
    public UserFile rename(long userId, String fileId, String name) {
        UserFile file = requireOwnedForUpdate(fileId, userId);
        if (file.getStatus() != UserFileStatus.AVAILABLE) throw conflict();
        file.rename(name);
        return fileRepository.saveAndFlush(file);
    }

    private UserStorageUsage requireUsageForUpdate(long userId) {
        return usageRepository.findForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Storage usage row is missing"));
    }

    private UserFile requireOwnedForUpdate(String fileId, long userId) {
        return fileRepository.findOwnedForUpdate(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private BusinessException conflict() { return new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT); }
}
