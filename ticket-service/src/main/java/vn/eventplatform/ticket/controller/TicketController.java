package vn.eventplatform.ticket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.service.TicketService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(@PathVariable("id") UUID id) {
        TicketResponse data = ticketService.getTicketById(id);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getTicketsByOrderId(@PathVariable("orderId") UUID orderId) {
        List<TicketResponse> data = ticketService.getTicketsByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping(value = "/{id}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getTicketQrImage(
            @PathVariable("id") UUID id,
            @RequestParam(name = "width", defaultValue = "300") int width,
            @RequestParam(name = "height", defaultValue = "300") int height
    ) {
        byte[] imageBytes = ticketService.getTicketQrImage(id, width, height);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"ticket-qr-" + id + ".png\"")
                .body(imageBytes);
    }
}
