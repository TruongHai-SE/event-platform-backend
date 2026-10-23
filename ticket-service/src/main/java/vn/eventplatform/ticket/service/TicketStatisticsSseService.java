package vn.eventplatform.ticket.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface TicketStatisticsSseService {

    /**
     * Đăng ký kết nối SSE nhận dữ liệu thống kê vé realtime theo sự kiện và suất diễn.
     *
     * @param eventId    ID của sự kiện (tùy chọn)
     * @param showTimeId ID của suất diễn (tùy chọn)
     * @return SseEmitter duy trì kết nối stream
     */
    SseEmitter subscribe(UUID eventId, UUID showTimeId);

    /**
     * Broadcast dữ liệu thống kê mới nhất tới các client đang theo dõi eventId / showTimeId tương ứng.
     *
     * @param eventId    ID của sự kiện vừa có vé check-in
     * @param showTimeId ID của suất diễn vừa có vé check-in
     */
    void broadcastUpdate(UUID eventId, UUID showTimeId);
}
