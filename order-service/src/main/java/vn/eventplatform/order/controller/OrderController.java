package vn.eventplatform.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderCountdownResponse;
import vn.eventplatform.order.dto.OrderFilterRequest;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.service.OrderService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "Các API phục vụ tạo và quản lý đơn hàng giữ chỗ vé")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách đơn hàng có lọc và phân trang (User chỉ xem đơn của mình, Admin/Staff có thể xem tất cả)")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "orderCode", required = false) String orderCode,
            @RequestParam(value = "eventId", required = false) UUID eventId,
            @RequestParam(value = "showTimeId", required = false) UUID showTimeId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            Authentication authentication,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        Pageable cappedPageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), 100),
                pageable.getSort()
        );

        OrderFilterRequest filter = OrderFilterRequest.builder()
                .userId(userId)
                .status(status)
                .orderCode(orderCode)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .keyword(keyword)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        Page<OrderResponse> page = orderService.getOrders(filter, currentUserId, isAdminOrStaff, cappedPageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

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

    @GetMapping("/{id}/countdown")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin đếm ngược thời gian giữ chỗ vé của đơn hàng theo ID")
    public ResponseEntity<ApiResponse<OrderCountdownResponse>> getOrderCountdown(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        OrderCountdownResponse response = orderService.getOrderCountdown(id, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/code/{orderCode}/countdown")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin đếm ngược thời gian giữ chỗ vé của đơn hàng theo mã đơn hàng")
    public ResponseEntity<ApiResponse<OrderCountdownResponse>> getOrderCountdownByCode(
            @PathVariable("orderCode") String orderCode,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        OrderCountdownResponse response = orderService.getOrderCountdownByCode(orderCode, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hủy đơn hàng giữ chỗ vé khi chưa thanh toán và giải phóng tồn kho (theo ID)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        OrderResponse response = orderService.cancelOrder(id, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok("Hủy đơn hàng thành công, tồn kho vé đã được giải phóng", response));
    }

    @PostMapping("/code/{orderCode}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hủy đơn hàng giữ chỗ vé khi chưa thanh toán và giải phóng tồn kho (theo mã đơn hàng)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrderByCode(
            @PathVariable("orderCode") String orderCode,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        OrderResponse response = orderService.cancelOrderByCode(orderCode, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok("Hủy đơn hàng thành công, tồn kho vé đã được giải phóng", response));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách đơn hàng của người dùng hiện tại có hỗ trợ lọc và phân trang")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "orderCode", required = false) String orderCode,
            @RequestParam(value = "eventId", required = false) UUID eventId,
            @RequestParam(value = "showTimeId", required = false) UUID showTimeId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            Authentication authentication,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = extractUserId(authentication);
        Pageable cappedPageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), 100),
                pageable.getSort()
        );

        OrderFilterRequest filter = OrderFilterRequest.builder()
                .userId(userId)
                .status(status)
                .orderCode(orderCode)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .keyword(keyword)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        Page<OrderResponse> page = orderService.getMyOrders(userId, filter, cappedPageable);
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
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ADMIN".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority())
                        || "STAFF".equals(a.getAuthority()));
    }
}
