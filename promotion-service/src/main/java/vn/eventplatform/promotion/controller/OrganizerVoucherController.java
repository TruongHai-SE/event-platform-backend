package vn.eventplatform.promotion.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.promotion.dto.promotion.PromotionRequest;
import vn.eventplatform.promotion.dto.promotion.PromotionResponse;
import vn.eventplatform.promotion.dto.promotion.PromotionUsageResponse;
import vn.eventplatform.promotion.service.promotion.impl.PromotionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer/vouchers")
@PreAuthorize("hasRole('ORGANIZER')")
@RequiredArgsConstructor
@Tag(name = "Voucher Organizer", description = "Organizer tạo, quản lý voucher và xem lịch sử sử dụng voucher của mình")
public class OrganizerVoucherController {
    private final PromotionService promotionService;

    @PostMapping
    @Operation(summary = "Tạo voucher cho Organizer hiện tại")
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tạo voucher thành công", promotionService.createForOrganizer(request)));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách voucher của Organizer hiện tại")
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.findAllForOrganizer()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết voucher của Organizer")
    public ResponseEntity<ApiResponse<PromotionResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.findByIdForOrganizer(id)));
    }

    @GetMapping("/{id}/usages")
    @Operation(summary = "Xem lịch sử voucher được dùng theo người dùng và đơn hàng")
    public ResponseEntity<ApiResponse<List<PromotionUsageResponse>>> usageHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.findUsageHistoryForOrganizer(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật voucher của Organizer")
    public ResponseEntity<ApiResponse<PromotionResponse>> update(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật voucher thành công", promotionService.updateForOrganizer(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Ngừng hoạt động voucher của Organizer")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        promotionService.deleteForOrganizer(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã ngừng hoạt động voucher", null));
    }
}
