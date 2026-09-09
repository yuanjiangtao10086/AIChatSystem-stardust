package com.example.stardust_springboot.knowledge.repository;

import com.example.stardust_springboot.knowledge.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    @Modifying
    @Query("delete from DocumentChunk c where c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
