package com.example.stardust_springboot.ai.repository;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.entity.ModelStatus;
import com.example.stardust_springboot.ai.entity.ModelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    Optional<AiModel> findByCode(String code);
    Optional<AiModel> findByPublicId(String publicId);

    @Query("""
            select model from AiModel model join fetch model.provider provider
            where model.publicId = :publicId
              and model.status = com.example.stardust_springboot.ai.entity.ModelStatus.ENABLED
              and model.modelType = com.example.stardust_springboot.ai.entity.ModelType.CHAT
              and provider.status = com.example.stardust_springboot.ai.entity.ProviderStatus.ENABLED
            """)
    Optional<AiModel> findEnabledChatModel(@Param("publicId") String publicId);

    @Query("""
            select model from AiModel model join fetch model.provider provider
            where model.status = com.example.stardust_springboot.ai.entity.ModelStatus.ENABLED
              and model.modelType = com.example.stardust_springboot.ai.entity.ModelType.CHAT
              and provider.status = com.example.stardust_springboot.ai.entity.ProviderStatus.ENABLED
            order by model.sortOrder asc, model.id asc
            """)
    List<AiModel> findEnabledChatModels();

    /** Full catalog for the console, provider eagerly fetched so no lazy load leaks past the tx. */
    @Query("""
            select model from AiModel model
            join fetch model.provider provider
            order by provider.code asc, model.sortOrder asc, model.id asc
            """)
    List<AiModel> findCatalog();

    List<AiModel> findByProviderIdOrderBySortOrderAscIdAsc(Long providerId);

    List<AiModel> findByModelTypeAndIsDefaultTrue(ModelType modelType);

    long countByProviderId(Long providerId);

    long countByStatus(ModelStatus status);

    Optional<AiModel> findByProviderIdAndExternalModelId(Long providerId, String externalModelId);
}
