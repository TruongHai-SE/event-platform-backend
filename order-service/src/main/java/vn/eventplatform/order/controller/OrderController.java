package vn.eventplatform.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.service.OrderService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "Các API phục vụ tạo và quản lý đơn hàng giữ chỗ vé")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo đơn hàng mua vé (giữ chỗ 15 phút)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        UUID userId = extractUserId(authentication);
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo đơn hàng thành công, vé được giữ chỗ trong 15 phút", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem chi tiết đơn hàng theo ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        OrderResponse response = orderService.getOrderById(id, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/code/{orderCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tra cứu đơn hàng theo mã đơn hàng")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(
            @PathVariable("orderCode") String orderCode,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        OrderResponse response = orderService.getOrderByCode(orderCode, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách đơn hàng của người dùng hiện tại")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            Authentication authentication,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = extractUserId(authentication);
        Page<OrderResponse> page = orderService.getMyOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        if (principal instanceof String principalStr) {
            return UUID.fromString(principalStr);
        }
        return UUID.fromString(authentication.getName());
    }

    private boolean checkAdminOrStaff(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF".equals(a.getAuthority()));
    }
}
