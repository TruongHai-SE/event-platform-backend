package vn.eventplatform.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventCompletionScheduler {

    private final EventService eventService;

    @Scheduled(fixedDelayString = "${event.scheduler.mark-ended-delay-ms:60000}")
    public void autoMarkEndedEvents() {
        int count = eventService.markEndedEventsNow();
        if (count > 0) {
            log.info("Marked {} published events as ENDED", count);
        }
    }
}
