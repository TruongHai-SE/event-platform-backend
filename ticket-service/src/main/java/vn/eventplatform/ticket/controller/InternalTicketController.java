package vn.eventplatform.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.service.TicketService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketService ticketService;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @PostMapping("/cancel-by-show-times")
    public ResponseEntity<ApiResponse<CancelTicketsResponse>> cancelTicketsByShowTimes(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody CancelTicketsRequest request
    ) {
        validateInternalSecret(secret);
        CancelTicketsResponse data = ticketService.cancelTicketsByShowTimes(request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/issue")
    public ResponseEntity<ApiResponse<TicketResponse>> issueTicket(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody IssueTicketRequest request
    ) {
        validateInternalSecret(secret);
        TicketResponse data = ticketService.issueTicket(request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/issue-batch")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> issueTickets(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody List<IssueTicketRequest> requests
    ) {
        validateInternalSecret(secret);
        List<TicketResponse> data = ticketService.issueTickets(requests);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/orders/{orderId}/has-used")
    public ResponseEntity<ApiResponse<Boolean>> hasUsedTickets(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable UUID orderId
    ) {
        validateInternalSecret(secret);
        boolean hasUsed = ticketService.hasUsedTickets(orderId);
        return ResponseEntity.ok(ApiResponse.ok(hasUsed));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Integer>> cancelTicketsByOrderId(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable UUID orderId,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        validateInternalSecret(secret);
        int count = ticketService.cancelTicketsByOrderId(orderId, reason != null ? reason : "Hoàn vé / hủy đơn");
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    private void validateInternalSecret(String secret) {
        if (secret == null || !secret.equals(internalSecret)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Khóa xác thực nội bộ không hợp lệ");
        }
    }
}
