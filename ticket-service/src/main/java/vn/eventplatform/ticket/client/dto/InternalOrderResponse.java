package vn.eventplatform.ticket.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalOrderResponse {

    private UUID id;
    private String orderCode;
    private UUID userId;
    private UUID eventId;
    private UUID showTimeId;
    private BigDecimal totalAmount;
    private String status;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Instant expiresAt;
}
