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
              and (:userSearch is null or lower(kb.user.displayName) like lower(concat('%', :userSearch, '%')) or lower(kb.user.emailNormalized) like lower(concat('%', :userSearch, '%')))
              and (:hideSuperAdminOwned = false or not exists (
                    select ur.id from UserRole ur
                    where ur.user = kb.user
                      and ur.role.code = 'SUPER_ADMIN'
                      and ur.role.status = com.example.stardust_springboot.user.entity.RoleStatus.ENABLED))
            """)
    Page<KnowledgeBase> findAdmin(@Param("userId") String userId,
                                  @Param("status") KnowledgeBaseStatus status,
                                  @Param("search") String search,
                                  @Param("userSearch") String userSearch,
                                  @Param("hideSuperAdminOwned") boolean hideSuperAdminOwned,
                                  Pageable pageable);

    /** Eagerly fetches the owner so an administrator detail view needs no lazy loading outside a transaction. */
    @Query("""
            select kb from KnowledgeBase kb
            join fetch kb.user
            where kb.publicId = :publicId and kb.deletedAt is null
            """)
    Optional<KnowledgeBase> findAdminDetail(@Param("publicId") String publicId);
}
