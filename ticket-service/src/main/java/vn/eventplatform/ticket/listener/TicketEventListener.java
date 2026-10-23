package vn.eventplatform.ticket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.eventplatform.ticket.event.TicketCheckedInEvent;
import vn.eventplatform.ticket.service.TicketStatisticsSseService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventListener {

    private final TicketStatisticsSseService ticketStatisticsSseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleTicketCheckedIn(TicketCheckedInEvent event) {
        log.debug("Nhận sự kiện check-in thành công: eventId={}, showTimeId={}, ticketId={}",
                event.eventId(), event.showTimeId(), event.ticketId());
        ticketStatisticsSseService.broadcastUpdate(event.eventId(), event.showTimeId());
    }
}
