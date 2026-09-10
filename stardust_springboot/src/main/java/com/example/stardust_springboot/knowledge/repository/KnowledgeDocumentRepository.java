package com.example.stardust_springboot.knowledge.repository;

import com.example.stardust_springboot.knowledge.entity.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocumentStatus;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findByPublicIdAndUserIdAndDeletedAtIsNull(String id, Long userId);
    Page<KnowledgeDocument> findByKnowledgeBaseIdAndUserIdAndDeletedAtIsNull(
            Long knowledgeBaseId, Long userId, Pageable pageable);
    boolean existsByKnowledgeBaseIdAndUserIdAndDeletedAtIsNull(Long knowledgeBaseId, Long userId);
    boolean existsByUserFileIdAndUserIdAndDeletedAtIsNull(Long fileId, Long userId);
    long countByUserFileIdAndDeletedAtIsNull(Long fileId);
    boolean existsByKnowledgeBaseIdAndUserFileIdAndDeletedAtIsNull(Long knowledgeBaseId, Long fileId);

    @Query("""
            select document.publicId from KnowledgeDocument document
            where document.user.id = :userId
              and document.knowledgeBase.publicId in :baseIds
              and document.publicId in :documentIds
              and document.status = :status
              and document.deletedAt is null
              and document.knowledgeBase.deletedAt is null
            """)
    List<String> findReadyPublicIds(@Param("userId") Long userId,
                                    @Param("baseIds") Collection<String> baseIds,
                                    @Param("documentIds") Collection<String> documentIds,
                                    @Param("status") KnowledgeDocumentStatus status);

    Optional<KnowledgeDocument> findByPublicIdAndDeletedAtIsNull(String id);

    @Query("""
            select document from KnowledgeDocument document where document.deletedAt is null
              and (:userId is null or document.user.publicId = :userId)
              and (:baseId is null or document.knowledgeBase.publicId = :baseId)
              and (:status is null or document.status = :status)
              and (:search is null or lower(document.userFile.originalName) like lower(concat('%', :search, '%')))
              and (:userSearch is null or lower(document.user.displayName) like lower(concat('%', :userSearch, '%')) or lower(document.user.emailNormalized) like lower(concat('%', :userSearch, '%')))
              and (:hideSuperAdminOwned = false or not exists (
                    select ur.id from UserRole ur
                    where ur.user = document.user
                      and ur.role.code = 'SUPER_ADMIN'
                      and ur.role.status = com.example.stardust_springboot.user.entity.RoleStatus.ENABLED))
            """)
    Page<KnowledgeDocument> findAdmin(@Param("userId") String userId,
                                      @Param("baseId") String baseId,
                                      @Param("status") KnowledgeDocumentStatus status,
                                      @Param("search") String search,
                                      @Param("userSearch") String userSearch,
                                      @Param("hideSuperAdminOwned") boolean hideSuperAdminOwned,
                                      Pageable pageable);

    /** Eagerly fetches owner, base and source file so an administrator view needs no lazy loading. */
    @Query("""
            select document from KnowledgeDocument document
            join fetch document.user
            join fetch document.knowledgeBase
            join fetch document.userFile
            where document.publicId = :publicId and document.deletedAt is null
            """)
    Optional<KnowledgeDocument> findAdminDetail(@Param("publicId") String publicId);

    long countByKnowledgeBaseIdAndDeletedAtIsNull(Long knowledgeBaseId);

    long countByKnowledgeBaseIdAndStatusAndDeletedAtIsNull(Long knowledgeBaseId,
                                                           KnowledgeDocumentStatus status);

    @Query("""
            select coalesce(sum(document.chunkCount), 0) from KnowledgeDocument document
            where document.knowledgeBase.id = :baseId and document.deletedAt is null
            """)
    long sumChunkCountByKnowledgeBaseIdAndDeletedAtIsNull(@Param("baseId") Long baseId);
}
