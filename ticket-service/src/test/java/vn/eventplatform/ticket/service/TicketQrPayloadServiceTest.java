package vn.eventplatform.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.dto.TicketQrPayloadDto;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketQrPayloadServiceTest {

    private TicketQrPayloadService qrPayloadService;
    private final String secret = "test_dummy_ticket_qr_secret_key_must_be_256_bits_long_abcdef123456";

    @BeforeEach
    void setUp() {
        qrPayloadService = new TicketQrPayloadServiceImpl(secret);
    }

    @Test
    @DisplayName("1. Sinh chuỗi token QR ký số thành công và giải mã kiểm tra đầy đủ các trường")
    void testGenerateAndVerifyPayload_Success() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String ticketTypeName = "VIP Golden Circle";
        String seatNumber = "A12";
        Instant issuedAt = Instant.now();

        TicketQrPayloadDto original = TicketQrPayloadDto.builder()
                .ticketId(ticketId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeId(ticketTypeId)
                .ticketTypeName(ticketTypeName)
                .orderId(orderId)
                .seatNumber(seatNumber)
                .issuedAt(issuedAt)
                .build();

        String token = qrPayloadService.generatePayload(original);
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT format: header.payload.signature

        TicketQrPayloadDto parsed = qrPayloadService.verifyAndParsePayload(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getTicketId()).isEqualTo(ticketId);
        assertThat(parsed.getEventId()).isEqualTo(eventId);
        assertThat(parsed.getShowTimeId()).isEqualTo(showTimeId);
        assertThat(parsed.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(parsed.getTicketTypeName()).isEqualTo(ticketTypeName);
        assertThat(parsed.getOrderId()).isEqualTo(orderId);
        assertThat(parsed.getSeatNumber()).isEqualTo(seatNumber);
    }

    @Test
    @DisplayName("2. Từ chối xác thực khi mã QR bị can thiệp/sửa đổi nội dung (Tampering)")
    void testVerifyPayload_TamperedToken_ThrowsException() {
        TicketQrPayloadDto original = TicketQrPayloadDto.builder()
                .ticketId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .showTimeId(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID())
                .ticketTypeName("Standard")
                .build();

        String token = qrPayloadService.generatePayload(original);
        // Thay đổi 1 ký tự trong payload token
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "X." + parts[2];

        assertThatThrownBy(() -> qrPayloadService.verifyAndParsePayload(tamperedToken))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã QR vé không hợp lệ hoặc đã bị can thiệp");
    }

    @Test
    @DisplayName("3. Từ chối xác thực khi mã QR được ký bằng secret key khác")
    void testVerifyPayload_DifferentSecret_ThrowsException() {
        TicketQrPayloadService attackerService = new TicketQrPayloadServiceImpl("attacker_fake_secret_key_must_be_256_bits_long_abcdef987654");

        TicketQrPayloadDto original = TicketQrPayloadDto.builder()
                .ticketId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .showTimeId(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID())
                .ticketTypeName("VIP")
                .build();

        String fakeToken = attackerService.generatePayload(original);

        assertThatThrownBy(() -> qrPayloadService.verifyAndParsePayload(fakeToken))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã QR vé không hợp lệ hoặc đã bị can thiệp");
    }

    @Test
    @DisplayName("4. Ném ngoại lệ khi payload rỗng hoặc null")
    void testVerifyPayload_EmptyOrNull() {
        assertThatThrownBy(() -> qrPayloadService.verifyAndParsePayload(""))
                .isInstanceOf(AppException.class);

        assertThatThrownBy(() -> qrPayloadService.verifyAndParsePayload(null))
                .isInstanceOf(AppException.class);
    }
}
