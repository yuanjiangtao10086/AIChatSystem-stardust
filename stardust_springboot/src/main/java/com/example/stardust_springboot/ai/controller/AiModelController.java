package com.example.stardust_springboot.ai.controller;

import com.example.stardust_springboot.ai.dto.AiModelView;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.common.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/models")
public class AiModelController {
    private final AiModelRepository modelRepository;

    public AiModelController(AiModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @GetMapping
    public ApiResult<List<AiModelView>> list() {
        return ApiResult.success(modelRepository.findEnabledChatModels().stream().map(AiModelView::from).toList());
    }
}
