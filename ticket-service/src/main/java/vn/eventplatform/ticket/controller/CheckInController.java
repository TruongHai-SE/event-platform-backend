package vn.eventplatform.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.service.CheckInService;

import java.util.UUID;

@Tag(name = "Check-In API", description = "Các API kiểm tra vé, soát vé và xác thực vào cổng sự kiện")
@RestController
@RequestMapping("/api/v1/check-ins")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "Check-in vé vào cổng sự kiện: Xác thực mã QR, từ chối vé sai sự kiện hoặc sai suất diễn")
    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request,
            @RequestParam(name = "eventId", required = false) UUID queryEventId,
            @RequestParam(name = "showTimeId", required = false) UUID queryShowTimeId
    ) {
        CheckInRequest effectiveRequest = resolveEffectiveRequest(request, queryEventId, queryShowTimeId);
        CheckInResponse data = checkInService.processCheckIn(effectiveRequest);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(summary = "Xác thực / Preview vé trước khi check-in (không thay đổi trạng thái vé)")
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TicketResponse>> validateTicket(
            @RequestBody CheckInRequest request,
            @RequestParam(name = "eventId", required = false) UUID queryEventId,
            @RequestParam(name = "showTimeId", required = false) UUID queryShowTimeId
    ) {
        CheckInRequest effectiveRequest = resolveEffectiveRequest(request, queryEventId, queryShowTimeId);
        TicketResponse data = checkInService.validateTicket(effectiveRequest);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(summary = "Tra cứu thông tin check-in theo ID vé")
    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CheckInResponse>> getCheckInByTicketId(@PathVariable("ticketId") UUID ticketId) {
        CheckInResponse data = checkInService.getCheckInByTicketId(ticketId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private CheckInRequest resolveEffectiveRequest(CheckInRequest request, UUID queryEventId, UUID queryShowTimeId) {
        CheckInRequest effectiveRequest = request;
        if (effectiveRequest.getEventId() == null && queryEventId != null) {
            effectiveRequest = effectiveRequest.toBuilder().eventId(queryEventId).build();
        }
        if (effectiveRequest.getShowTimeId() == null && queryShowTimeId != null) {
            effectiveRequest = effectiveRequest.toBuilder().showTimeId(queryShowTimeId).build();
        }
        return effectiveRequest;
    }
}
