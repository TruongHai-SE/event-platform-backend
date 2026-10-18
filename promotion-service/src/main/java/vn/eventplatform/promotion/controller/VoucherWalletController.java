package vn.eventplatform.promotion.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.promotion.dto.voucher.VoucherIssueRequest;
import vn.eventplatform.promotion.dto.voucher.WalletVoucherResponse;
import vn.eventplatform.promotion.service.VoucherApplicationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Ví voucher", description = "Người dùng xem voucher trong ví; Admin có thể cấp voucher cho người dùng")
public class VoucherWalletController {
    private final VoucherApplicationService voucherApplicationService;

    @GetMapping("/api/v1/vouchers/wallet")
    @Operation(summary = "Xem các voucher đang có trong ví")
    public ResponseEntity<ApiResponse<List<WalletVoucherResponse>>> wallet(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.wallet(UUID.fromString(authentication.getPrincipal().toString()))));
    }

    @PostMapping("/api/v1/admin/vouchers/{promotionId}/issue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cấp voucher vào ví người dùng")
    public ResponseEntity<ApiResponse<WalletVoucherResponse>> issue(
            @PathVariable UUID promotionId, @Valid @RequestBody VoucherIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã cấp voucher vào ví người dùng", voucherApplicationService.issue(promotionId, request)));
    }
}
