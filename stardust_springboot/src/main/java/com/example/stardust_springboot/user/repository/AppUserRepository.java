package com.example.stardust_springboot.user.repository;

import com.example.stardust_springboot.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByPublicIdAndDeletedAtIsNull(String publicId);

    Optional<AppUser> findByEmailNormalizedAndDeletedAtIsNull(String emailNormalized);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<AppUser> findByPublicId(String publicId);
    long countByCreatedAtGreaterThanEqual(Instant since);
    long countByLastLoginAtGreaterThanEqualAndDeletedAtIsNull(Instant since);

    @Query("""
            select user from AppUser user
            where (:search is null or lower(user.emailNormalized) like lower(concat('%', :search, '%'))
                or lower(user.displayName) like lower(concat('%', :search, '%')))
              and (:status is null or user.status = :status)
              and (:role is null or exists (select 1 from UserRole ur
                    join ur.role role where ur.user.id = user.id and role.code = :role))
            """)
    Page<AppUser> findAdmin(@Param("search") String search,
                            @Param("status") com.example.stardust_springboot.user.entity.UserStatus status,
                            @Param("role") String role,
                            Pageable pageable);
}
