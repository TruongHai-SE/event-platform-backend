package vn.eventplatform.order.lock;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

public interface InventoryLockService {

    /**
     * Thực thi một tác vụ có trả về kết quả dưới sự bảo vệ của Redis Distributed Lock
     * theo cặp (showTimeId, ticketTypeIds).
     *
     * @param showTimeId    ID của suất diễn
     * @param ticketTypeIds Danh sách ID các hạng vé cần giữ chỗ
     * @param action        Tác vụ cần thực thi trong vùng đồng bộ
     * @param <T>           Kiểu dữ liệu trả về của tác vụ
     * @return Kết quả từ action
     */
    <T> T executeWithLock(UUID showTimeId, Collection<UUID> ticketTypeIds, Supplier<T> action);

    /**
     * Thực thi một tác vụ không trả về kết quả dưới sự bảo vệ của Redis Distributed Lock.
     *
     * @param showTimeId    ID của suất diễn
     * @param ticketTypeIds Danh sách ID các hạng vé cần giữ chỗ
     * @param action        Tác vụ cần thực thi
     */
    void executeWithLock(UUID showTimeId, Collection<UUID> ticketTypeIds, Runnable action);
}
