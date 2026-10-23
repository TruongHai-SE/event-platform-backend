package vn.eventplatform.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.eventplatform.ticket.dto.TicketStatisticsResponse;
import vn.eventplatform.ticket.event.TicketCheckedInEvent;
import vn.eventplatform.ticket.listener.TicketEventListener;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketStatisticsSseServiceTest {

    @Mock
    private TicketService ticketService;

    private TicketStatisticsSseServiceImpl sseService;

    @BeforeEach
    void setUp() {
        sseService = new TicketStatisticsSseServiceImpl(ticketService);
    }

    @Test
    @DisplayName("1. subscribe: Đăng ký thành công, nhận dữ liệu thống kê khởi tạo ngay lập tức")
    void testSubscribe_Success() {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();

        TicketStatisticsResponse stats = TicketStatisticsResponse.builder()
                .eventId(eventId)
                .showTimeId(showTimeId)
                .totalIssued(100L)
                .totalSold(80L)
                .totalCheckedIn(20L)
                .checkInRate(20.0)
                .build();

        when(ticketService.getTicketStatistics(eventId, showTimeId)).thenReturn(stats);

        SseEmitter emitter = sseService.subscribe(eventId, showTimeId);

        assertThat(emitter).isNotNull();
        verify(ticketService, times(1)).getTicketStatistics(eventId, showTimeId);
    }

    @Test
    @DisplayName("2. broadcastUpdate: Gửi dữ liệu thống kê mới tới subscriber của event tương ứng")
    void testBroadcastUpdate_MatchesSubscribers() {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();

        TicketStatisticsResponse initialStats = TicketStatisticsResponse.builder()
                .eventId(eventId)
                .totalIssued(100L)
                .totalCheckedIn(10L)
                .build();

        TicketStatisticsResponse updatedStats = TicketStatisticsResponse.builder()
                .eventId(eventId)
                .totalIssued(100L)
                .totalCheckedIn(11L)
                .build();

        when(ticketService.getTicketStatistics(eventId, null)).thenReturn(initialStats, updatedStats);

        SseEmitter emitter = sseService.subscribe(eventId, null);
        assertThat(emitter).isNotNull();

        // Broadcast check-in event
        sseService.broadcastUpdate(eventId, showTimeId);

        // ticketService should be called once for initial subscribe, and once for broadcast
        verify(ticketService, times(2)).getTicketStatistics(eventId, null);
    }

    @Test
    @DisplayName("3. broadcastUpdate: Không gửi cho subscriber của event khác")
    void testBroadcastUpdate_IgnoresDifferentEvent() {
        UUID eventIdA = UUID.randomUUID();
        UUID eventIdB = UUID.randomUUID();

        when(ticketService.getTicketStatistics(eventIdA, null)).thenReturn(TicketStatisticsResponse.builder().build());

        sseService.subscribe(eventIdA, null);

        // Broadcast cho eventIdB
        sseService.broadcastUpdate(eventIdB, UUID.randomUUID());

        // ticketService chỉ được gọi 1 lần cho eventIdA (lúc subscribe ban đầu)
        verify(ticketService, times(1)).getTicketStatistics(eventIdA, null);
        verify(ticketService, never()).getTicketStatistics(eq(eventIdB), any());
    }

    @Test
    @DisplayName("4. sendHeartbeat: Gửi ping comment không gây lỗi khi có subscriber")
    void testSendHeartbeat_Success() {
        UUID eventId = UUID.randomUUID();
        when(ticketService.getTicketStatistics(eventId, null)).thenReturn(TicketStatisticsResponse.builder().build());

        sseService.subscribe(eventId, null);
        sseService.sendHeartbeat();

        // Không throw exception
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("5. TicketEventListener: Nhận TicketCheckedInEvent và gọi sseService.broadcastUpdate")
    void testTicketEventListener_Success() {
        TicketStatisticsSseService mockSseService = mock(TicketStatisticsSseService.class);
        TicketEventListener listener = new TicketEventListener(mockSseService);

        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        TicketCheckedInEvent event = new TicketCheckedInEvent(eventId, showTimeId, ticketId, staffId, Instant.now());
        listener.handleTicketCheckedIn(event);

        verify(mockSseService, times(1)).broadcastUpdate(eventId, showTimeId);
    }
}
