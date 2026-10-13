package vn.eventplatform.ticket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.service.CheckInService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/check-ins")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        CheckInResponse data = checkInService.processCheckIn(request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CheckInResponse>> getCheckInByTicketId(@PathVariable("ticketId") UUID ticketId) {
        CheckInResponse data = checkInService.getCheckInByTicketId(ticketId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
