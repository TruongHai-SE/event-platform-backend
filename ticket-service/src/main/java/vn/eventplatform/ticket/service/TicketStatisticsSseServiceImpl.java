package vn.eventplatform.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.eventplatform.ticket.dto.TicketStatisticsResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketStatisticsSseServiceImpl implements TicketStatisticsSseService {

    private static final Long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30 phút

    private final TicketService ticketService;
    private final List<SseSubscription> subscriptions = new CopyOnWriteArrayList<>();

    private record SseSubscription(UUID eventId, UUID showTimeId, SseEmitter emitter) {}

    @Override
    public SseEmitter subscribe(UUID eventId, UUID showTimeId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        SseSubscription sub = new SseSubscription(eventId, showTimeId, emitter);
        subscriptions.add(sub);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed: eventId={}, showTimeId={}", eventId, showTimeId);
            subscriptions.remove(sub);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out: eventId={}, showTimeId={}", eventId, showTimeId);
            emitter.complete();
            subscriptions.remove(sub);
        });

        emitter.onError(e -> {
            log.debug("SSE connection error: eventId={}, showTimeId={}, err={}", eventId, showTimeId, e.getMessage());
            emitter.complete();
            subscriptions.remove(sub);
        });

        // Gửi ngay dữ liệu thống kê hiện tại khi vừa kết nối thành công
        try {
            TicketStatisticsResponse initialStats = ticketService.getTicketStatistics(eventId, showTimeId);
            emitter.send(SseEmitter.event()
                    .name("statistics")
                    .data(initialStats));
        } catch (Exception e) {
            log.warn("Không thể gửi dữ liệu thống kê ban đầu qua SSE: {}", e.getMessage());
            emitter.complete();
            subscriptions.remove(sub);
        }

        return emitter;
    }

    @Override
    public void broadcastUpdate(UUID eventId, UUID showTimeId) {
        if (subscriptions.isEmpty()) {
            return;
        }

        // Cache tính toán thống kê trong 1 chu kỳ broadcast để tránh query DB lặp lại
        Map<String, TicketStatisticsResponse> statsCache = new HashMap<>();
        List<SseSubscription> deadSubscriptions = new ArrayList<>();

        for (SseSubscription sub : subscriptions) {
            // Kiểm tra subscription có quan tâm tới eventId này không
            // (Nếu sub.eventId == null -> quan tâm toàn bộ hệ thống; nếu có eventId -> phải khớp)
            if (sub.eventId() != null && !Objects.equals(sub.eventId(), eventId)) {
                continue;
            }

            // Nếu sub có filter theo showTimeId thì kiểm tra khớp suất diễn
            if (sub.showTimeId() != null && showTimeId != null && !Objects.equals(sub.showTimeId(), showTimeId)) {
                continue;
            }

            String cacheKey = (sub.eventId() != null ? sub.eventId() : "ALL") + ":"
                    + (sub.showTimeId() != null ? sub.showTimeId() : "ALL");

            try {
                TicketStatisticsResponse stats = statsCache.computeIfAbsent(cacheKey,
                        k -> ticketService.getTicketStatistics(sub.eventId(), sub.showTimeId()));

                sub.emitter().send(SseEmitter.event()
                        .name("statistics")
                        .data(stats));
            } catch (IOException | IllegalStateException e) {
                log.info("Lỗi gửi SSE tới client (kết nối đã đóng): {}", e.getMessage());
                deadSubscriptions.add(sub);
            } catch (Exception e) {
                log.error("Lỗi khi xử lý broadcast thống kê SSE: {}", e.getMessage());
            }
        }

        if (!deadSubscriptions.isEmpty()) {
            subscriptions.removeAll(deadSubscriptions);
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        if (subscriptions.isEmpty()) {
            return;
        }
        List<SseSubscription> deadSubscriptions = new ArrayList<>();
        for (SseSubscription sub : subscriptions) {
            try {
                sub.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                deadSubscriptions.add(sub);
            }
        }
        if (!deadSubscriptions.isEmpty()) {
            subscriptions.removeAll(deadSubscriptions);
        }
    }
}
