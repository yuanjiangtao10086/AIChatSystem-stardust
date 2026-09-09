package com.example.stardust_springboot.memory.repository;

import com.example.stardust_springboot.memory.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface UserMemoryRepository extends JpaRepository<UserMemory,Long> {
    Optional<UserMemory> findByPublicIdAndUserIdAndDeletedAtIsNull(String id, Long userId);
    Optional<UserMemory> findFirstByUserIdAndContentHashAndDeletedAtIsNull(Long userId, String hash);
    @Query("""
      select m from UserMemory m where m.user.id=:userId and m.deletedAt is null
      and (:enabled is null or m.enabled=:enabled) and (:type is null or m.memoryType=:type)
      and (:search is null or lower(m.content) like lower(concat('%',:search,'%'))
        or lower(m.summary) like lower(concat('%',:search,'%')))
      """)
    Page<UserMemory> findOwned(@Param("userId") Long userId, @Param("search") String search,
      @Param("enabled") Boolean enabled, @Param("type") MemoryType type, Pageable pageable);
    @Query("""
      select m from UserMemory m where m.user.id=:userId and m.enabled=true and m.deletedAt is null
      order by m.importance desc, m.updatedAt desc, m.id desc
      """)
    List<UserMemory> findRetrievalCandidates(@Param("userId") Long userId, Pageable pageable);
}
