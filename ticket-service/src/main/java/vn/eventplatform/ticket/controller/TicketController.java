package vn.eventplatform.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.TicketFilterRequest;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.service.TicketService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Tag(name = "Ticket API", description = "Các API tra cứu chi tiết vé, ví vé khách hàng, tìm kiếm vé và hiển thị QR vé điện tử")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "Quản lý vé dành cho Admin, Staff, Organizer: Tra cứu, tìm kiếm và lọc danh sách vé")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'ORGANIZER')")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> searchTickets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "eventId", required = false) UUID eventId,
            @RequestParam(value = "showTimeId", required = false) UUID showTimeId,
            @RequestParam(value = "ticketTypeId", required = false) UUID ticketTypeId,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Pageable cappedPageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), 100),
                pageable.getSort()
        );

        TicketFilterRequest filter = TicketFilterRequest.builder()
                .keyword(keyword)
                .status(status)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeId(ticketTypeId)
                .userId(userId)
                .orderId(orderId)
                .build();

        Page<TicketResponse> page = ticketService.searchTickets(filter, cappedPageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @Operation(summary = "Lấy chi tiết vé theo ID (kiểm tra quyền sở hữu vé)")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        TicketResponse data = ticketService.getTicketDetail(id, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(summary = "Ví vé của khách hàng: Danh sách vé cá nhân có phân trang và bộ lọc")
    @GetMapping({"/my-tickets", "/wallet"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> getMyTickets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) TicketStatus status,
            @RequestParam(value = "eventId", required = false) UUID eventId,
            @RequestParam(value = "showTimeId", required = false) UUID showTimeId,
            Authentication authentication,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID currentUserId = extractUserId(authentication);
        Pageable cappedPageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), 100),
                pageable.getSort()
        );

        TicketFilterRequest filter = TicketFilterRequest.builder()
                .keyword(keyword)
                .status(status)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .build();

        Page<TicketResponse> page = ticketService.getMyTickets(currentUserId, filter, cappedPageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @Operation(summary = "Lấy danh sách vé theo mã đơn hàng (kiểm tra quyền sở hữu đơn hàng)")
    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getTicketsByOrderId(
            @PathVariable("orderId") UUID orderId,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        List<TicketResponse> data = ticketService.getTicketsByOrderId(orderId, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(summary = "Lấy trực tiếp ảnh mã QR PNG của vé")
    @GetMapping(value = "/{id}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getTicketQrImage(
            @PathVariable("id") UUID id,
            @RequestParam(name = "width", defaultValue = "300") int width,
            @RequestParam(name = "height", defaultValue = "300") int height,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        byte[] imageBytes = ticketService.getTicketQrImage(id, width, height, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"ticket-qr-" + id + ".png\"")
                .body(imageBytes);
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        Object principal = authentication.getPrincipal();
        String name = authentication.getName();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            name = userDetails.getUsername();
        } else if (principal instanceof String principalStr) {
            name = principalStr;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        }
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
