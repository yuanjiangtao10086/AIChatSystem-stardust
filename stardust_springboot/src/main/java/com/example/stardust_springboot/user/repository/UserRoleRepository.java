package com.example.stardust_springboot.user.repository;

import com.example.stardust_springboot.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Query("""
            select ur.role.code from UserRole ur
            where ur.user.id = :userId and ur.role.status = com.example.stardust_springboot.user.entity.RoleStatus.ENABLED
            """)
    List<String> findEnabledRoleCodesByUserId(Long userId);

    List<UserRole> findByUserId(Long userId);
    void deleteByUserIdAndRoleId(Long userId, Long roleId);
}
