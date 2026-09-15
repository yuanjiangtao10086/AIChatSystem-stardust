package com.example.stardust_springboot.memory.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.BatchDeleteRequest;
import com.example.stardust_springboot.common.api.BatchDeleteResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.memory.dto.*;
import com.example.stardust_springboot.memory.entity.MemoryType;
import com.example.stardust_springboot.memory.service.MemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/v1/memories")
public class MemoryController {
    private final MemoryService service;
    public MemoryController(MemoryService service) { this.service = service; }
    @GetMapping public ApiResult<PageResult<MemoryView>> list(@AuthenticationPrincipal AuthenticatedUser p,
      @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
      @RequestParam(required=false) @Size(max=200) String search,@RequestParam(required=false) Boolean enabled,
      @RequestParam(required=false) MemoryType type){return ApiResult.success(service.list(p,page,size,search,enabled,type));}
    @GetMapping("/{id}") public ApiResult<MemoryView> get(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable String id){return ApiResult.success(service.get(p,id));}
    @PostMapping public ResponseEntity<ApiResult<MemoryView>> create(@AuthenticationPrincipal AuthenticatedUser p,@Valid @RequestBody CreateMemoryRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(service.create(p,r)));}
    @PatchMapping("/{id}") public ApiResult<MemoryView> update(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable String id,@Valid @RequestBody UpdateMemoryRequest r){return ApiResult.success(service.update(p,id,r));}
    @PatchMapping("/{id}/enabled") public ApiResult<MemoryView> enabled(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable String id,@Valid @RequestBody MemoryEnabledRequest r){return ApiResult.success(service.enabled(p,id,r.enabled()));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser p,@PathVariable String id){service.delete(p,id);return ResponseEntity.noContent().build();}
    @DeleteMapping("/batch") public ApiResult<BatchDeleteResult> deleteBatch(@AuthenticationPrincipal AuthenticatedUser p,@Valid @RequestBody BatchDeleteRequest r){return ApiResult.success(service.batchDelete(p,r.ids()));}
}
