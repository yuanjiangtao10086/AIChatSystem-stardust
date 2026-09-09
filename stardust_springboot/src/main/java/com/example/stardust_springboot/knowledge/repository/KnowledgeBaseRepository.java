package com.example.stardust_springboot.knowledge.repository;

import com.example.stardust_springboot.knowledge.entity.KnowledgeBase;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    Optional<KnowledgeBase> findByPublicIdAndUserIdAndStatusAndDeletedAtIsNull(
            String publicId, Long userId, KnowledgeBaseStatus status);

    @Query("""
            select kb from KnowledgeBase kb
            where kb.user.id = :userId and kb.status = :status and kb.deletedAt is null
              and (:search is null or lower(kb.name) like lower(concat('%', :search, '%'))
                or lower(kb.description) like lower(concat('%', :search, '%')))
            """)
    Page<KnowledgeBase> findOwned(@Param("userId") Long userId,
                                  @Param("status") KnowledgeBaseStatus status,
                                  @Param("search") String search,
                                  Pageable pageable);

    List<KnowledgeBase> findByPublicIdInAndUserIdAndStatusAndDeletedAtIsNull(
            Collection<String> ids, Long userId, KnowledgeBaseStatus status);

    Optional<KnowledgeBase> findByPublicIdAndDeletedAtIsNull(String publicId);

    @Query("""
            select kb from KnowledgeBase kb where kb.deletedAt is null
              and (:userId is null or kb.user.publicId = :userId)
              and (:status is null or kb.status = :status)
              and (:search is null or lower(kb.name) like lower(concat('%', :search, '%')))
            """)
    Page<KnowledgeBase> findAdmin(@Param("userId") String userId,
                                  @Param("status") KnowledgeBaseStatus status,
                                  @Param("search") String search,
                                  Pageable pageable);
}
