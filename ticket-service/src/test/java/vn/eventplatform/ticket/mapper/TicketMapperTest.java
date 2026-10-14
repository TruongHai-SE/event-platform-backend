package vn.eventplatform.ticket.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import vn.eventplatform.ticket.domain.CheckIn;
import vn.eventplatform.ticket.domain.CheckInMethod;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TicketMapperImpl.class, CheckInMapperImpl.class})
class TicketMapperTest {

    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Test
    @DisplayName("Should correctly map IssueTicketRequest to Ticket Entity via MapStruct")
    void shouldMapIssueTicketRequestToEntity() {
        UUID ticketTypeId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        String qrPayload = "{\"ticketId\":\"test-uuid\",\"sig\":\"abc123hmac\"}";

        IssueTicketRequest request = IssueTicketRequest.builder()
                .ticketTypeId(ticketTypeId)
                .showTimeId(showTimeId)
                .orderId(orderId)
                .seatId(seatId)
                .seatNumber("VIP-A12")
                .qrPayload(qrPayload)
                .build();

        Ticket ticket = ticketMapper.toEntity(request);

        assertThat(ticket).isNotNull();
        assertThat(ticket.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(ticket.getShowTimeId()).isEqualTo(showTimeId);
        assertThat(ticket.getOrderId()).isEqualTo(orderId);
        assertThat(ticket.getSeatId()).isEqualTo(seatId);
        assertThat(ticket.getSeatNumber()).isEqualTo("VIP-A12");
        assertThat(ticket.getQrPayload()).isEqualTo(qrPayload);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
    }

    @Test
    @DisplayName("Should correctly map Ticket Entity with CheckIn to TicketResponse via MapStruct")
    void shouldMapTicketEntityWithCheckInToResponse() {
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Instant now = Instant.now();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .ticketTypeId(UUID.randomUUID())
                .showTimeId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .seatNumber("A01")
                .qrPayload("test-qr")
                .status(TicketStatus.USED)
                .build();

        CheckIn checkIn = CheckIn.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .checkedInAt(now)
                .createdAt(now)
                .build();

        ticket.setCheckIn(checkIn);

        TicketResponse response = ticketMapper.toResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ticketId);
        assertThat(response.getSeatNumber()).isEqualTo("A01");
        assertThat(response.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(response.getCheckIn()).isNotNull();
        assertThat(response.getCheckIn().getStaffId()).isEqualTo(staffId);
        assertThat(response.getCheckIn().getMethod()).isEqualTo(CheckInMethod.QR_SCAN);
        assertThat(response.getCheckIn().getSeatNumber()).isEqualTo("A01");
    }

    @Test
    @DisplayName("Should correctly map CheckIn Entity to CheckInResponse via MapStruct")
    void shouldMapCheckInEntityToResponse() {
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Instant checkedInAt = Instant.now();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .seatNumber("B15")
                .build();

        CheckIn checkIn = CheckIn.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .staffId(staffId)
                .method(CheckInMethod.MANUAL)
                .checkedInAt(checkedInAt)
                .createdAt(checkedInAt)
                .build();

        CheckInResponse response = checkInMapper.toResponse(checkIn);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getStaffId()).isEqualTo(staffId);
        assertThat(response.getMethod()).isEqualTo(CheckInMethod.MANUAL);
        assertThat(response.getSeatNumber()).isEqualTo("B15");
        assertThat(response.getCheckedInAt()).isEqualTo(checkedInAt);
    }
}
