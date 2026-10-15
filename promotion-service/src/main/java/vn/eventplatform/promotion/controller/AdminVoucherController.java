package vn.eventplatform.promotion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.promotion.dto.promotion.PromotionRequest;
import vn.eventplatform.promotion.dto.promotion.PromotionResponse;
import vn.eventplatform.promotion.service.promotion.impl.PromotionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVoucherController {
    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo voucher thành công", promotionService.createForAdmin(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.findAllForAdmin()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.findByIdForAdmin(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> update(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật voucher thành công", promotionService.updateForAdmin(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        promotionService.deleteForAdmin(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã ngừng hoạt động voucher", null));
    }
}
