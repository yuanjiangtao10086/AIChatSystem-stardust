package com.example.stardust_springboot.ai.repository;

import com.example.stardust_springboot.ai.entity.AiProvider;
import com.example.stardust_springboot.ai.entity.ProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    Optional<AiProvider> findByCode(String code);
    Optional<AiProvider> findByPublicId(String publicId);

    long countByStatus(ProviderStatus status);
}
