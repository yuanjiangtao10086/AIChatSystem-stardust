package com.example.stardust_springboot.file.repository;

import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserFileRepository extends JpaRepository<UserFile, Long> {
    Optional<UserFile> findByPublicIdAndUserIdAndStatusAndDeletedAtIsNull(
            String publicId, Long userId, UserFileStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select file from UserFile file
            where file.publicId = :publicId and file.user.id = :userId and file.deletedAt is null
            """)
    Optional<UserFile> findOwnedForUpdate(@Param("publicId") String publicId,
                                           @Param("userId") Long userId);

    @Query("""
            select file from UserFile file
            where file.user.id = :userId
              and file.status = com.example.stardust_springboot.file.entity.UserFileStatus.AVAILABLE
              and file.deletedAt is null
              and (:search is null or lower(file.originalName) like lower(concat('%', :search, '%')))
            """)
    Page<UserFile> findOwnedAvailable(@Param("userId") Long userId,
                                      @Param("search") String search,
                                      Pageable pageable);

    List<UserFile> findByPublicIdInAndUserIdAndStatusAndDeletedAtIsNull(
            Collection<String> publicIds, Long userId, UserFileStatus status);

    Optional<UserFile> findByPublicIdAndDeletedAtIsNull(String publicId);

    @Query("""
            select file from UserFile file
            where file.deletedAt is null
              and (:userId is null or file.user.publicId = :userId)
              and (:mime is null or lower(file.detectedMime) like lower(concat(:mime, '%')))
              and (:status is null or file.status = :status)
              and (:search is null or lower(file.originalName) like lower(concat('%', :search, '%')))
            """)
    Page<UserFile> findAdmin(@Param("userId") String userId,
                             @Param("mime") String mime,
                             @Param("status") UserFileStatus status,
                             @Param("search") String search,
                             Pageable pageable);
}
