package vn.eventplatform.promotion.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.promotion.dto.voucher.*;
import vn.eventplatform.promotion.service.InternalRequestGuard;
import vn.eventplatform.promotion.service.VoucherApplicationService;

@RestController
@RequestMapping("/internal/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Voucher nội bộ", description = "API nội bộ dành cho order-service kiểm tra, áp dụng và giải phóng voucher")
public class InternalVoucherController {
    private final VoucherApplicationService voucherApplicationService;
    private final InternalRequestGuard internalRequestGuard;

    @PostMapping("/validate")
    @Operation(summary = "Kiểm tra voucher trước khi tạo đơn hàng")
    public ResponseEntity<ApiResponse<VoucherCalculationResponse>> validate(
            @RequestHeader("X-Internal-Secret") String secret, @Valid @RequestBody VoucherValidationRequest request) {
        internalRequestGuard.verify(secret);
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.validate(request)));
    }

    @PostMapping("/apply")
    @Operation(summary = "Áp dụng voucher cho đơn hàng")
    public ResponseEntity<ApiResponse<VoucherCalculationResponse>> apply(
            @RequestHeader("X-Internal-Secret") String secret, @Valid @RequestBody VoucherApplyRequest request) {
        internalRequestGuard.verify(secret);
        return ResponseEntity.ok(ApiResponse.ok("Áp dụng voucher thành công", voucherApplicationService.apply(request)));
    }

    @PostMapping("/release")
    @Operation(summary = "Giải phóng lượt voucher khi đơn bị hủy hoặc hết hạn")
    public ResponseEntity<ApiResponse<Void>> release(
            @RequestHeader("X-Internal-Secret") String secret, @Valid @RequestBody VoucherReleaseRequest request) {
        internalRequestGuard.verify(secret);
        voucherApplicationService.release(request);
        return ResponseEntity.ok(ApiResponse.ok("Đã giải phóng voucher", null));
    }
}
