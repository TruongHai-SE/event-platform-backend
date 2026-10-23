package vn.eventplatform.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.dto.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EventServiceFallback implements EventServiceClient {

    public static class MockInventoryItem {
        public UUID ticketTypeId;
        public String ticketTypeName;
        public BigDecimal price;
        public int maxPerOrder;
        public int totalQuantity;
        public int reservedQuantity;
        public int soldQuantity;

        public MockInventoryItem(UUID ticketTypeId, String ticketTypeName, BigDecimal price, int maxPerOrder, int totalQuantity) {
            this.ticketTypeId = ticketTypeId;
            this.ticketTypeName = ticketTypeName;
            this.price = price;
            this.maxPerOrder = maxPerOrder;
            this.totalQuantity = totalQuantity;
            this.reservedQuantity = 0;
            this.soldQuantity = 0;
        }

        public int getAvailableQuantity() {
            return totalQuantity - (reservedQuantity + soldQuantity);
        }
    }

    private final Map<String, MockInventoryItem> inventoryStore = new ConcurrentHashMap<>();

    public void seedInventory(UUID showTimeId, UUID ticketTypeId, String name, BigDecimal price, int totalQuantity, int maxPerOrder) {
        String key = buildKey(showTimeId, ticketTypeId);
        MockInventoryItem item = new MockInventoryItem(ticketTypeId, name, price, maxPerOrder, totalQuantity);
        inventoryStore.put(key, item);
    }

    private String buildKey(UUID showTimeId, UUID ticketTypeId) {
        return (showTimeId != null ? showTimeId.toString() : "default") + "_" + ticketTypeId.toString();
    }

    private MockInventoryItem getOrCreateMockItem(UUID showTimeId, UUID ticketTypeId) {
        String key = buildKey(showTimeId, ticketTypeId);
        return inventoryStore.computeIfAbsent(key, k ->
                new MockInventoryItem(
                        ticketTypeId,
                        "Hạng vé chuẩn",
                        new BigDecimal("250000"),
                        10,
                        100
                )
        );
    }

    @Override
    public ApiResponse<CheckAndReserveInventoryResponse> checkAndReserveInventory(
            String secret,
            CheckAndReserveInventoryRequest request
    ) {
        log.info("[EventServiceFallback] Kiểm tra tồn kho & giữ chỗ cho showTimeId={}, số lượng hạng vé={}",
                request.getShowTimeId(), request.getItems() != null ? request.getItems().size() : 0);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh sách hạng vé cần giữ chỗ không được để trống");
        }

        List<TicketTypeInventoryDto> reservedItems = new ArrayList<>();

        synchronized (this) {
            // Bước 1: Kiểm tra tính hợp lệ và tồn kho khả dụng của TẤT CẢ các hạng vé trước
            for (TicketItemReservationRequest itemReq : request.getItems()) {
                MockInventoryItem item = getOrCreateMockItem(request.getShowTimeId(), itemReq.getTicketTypeId());

                if (itemReq.getQuantity() > item.maxPerOrder) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            String.format("Hạng vé '%s' chỉ cho phép mua tối đa %d vé mỗi đơn (yêu cầu: %d)",
                                    item.ticketTypeName, item.maxPerOrder, itemReq.getQuantity()));
                }

                int available = item.getAvailableQuantity();
                if (itemReq.getQuantity() > available) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            String.format("Hạng vé '%s' không đủ số lượng vé khả dụng (còn lại: %d, yêu cầu: %d)",
                                    item.ticketTypeName, available, itemReq.getQuantity()));
                }
            }

            // Bước 2: Tạm giữ chỗ (tăng reservedQuantity) và lấy snapshot đơn giá chính thức
            for (TicketItemReservationRequest itemReq : request.getItems()) {
                MockInventoryItem item = getOrCreateMockItem(request.getShowTimeId(), itemReq.getTicketTypeId());
                item.reservedQuantity += itemReq.getQuantity();

                reservedItems.add(TicketTypeInventoryDto.builder()
                        .ticketTypeId(item.ticketTypeId)
                        .ticketTypeName(item.ticketTypeName)
                        .price(item.price)
                        .maxPerOrder(item.maxPerOrder)
                        .availableQuantity(item.getAvailableQuantity())
                        .build());
            }
        }

        return ApiResponse.ok(CheckAndReserveInventoryResponse.builder()
                .success(true)
                .reservedItems(reservedItems)
                .build());
    }

    @Override
    public ApiResponse<Void> releaseInventory(String secret, ReleaseInventoryRequest request) {
        log.info("[EventServiceFallback] Nhả giữ chỗ cho showTimeId={}, số lượng={}",
                request.getShowTimeId(), request.getItems() != null ? request.getItems().size() : 0);

        if (request.getItems() != null) {
            synchronized (this) {
                for (TicketItemReservationRequest itemReq : request.getItems()) {
                    String key = buildKey(request.getShowTimeId(), itemReq.getTicketTypeId());
                    MockInventoryItem item = inventoryStore.get(key);
                    if (item != null) {
                        item.reservedQuantity = Math.max(0, item.reservedQuantity - itemReq.getQuantity());
                    }
                }
            }
        }

        return ApiResponse.ok(null);
    }

    @Override
    public ApiResponse<List<ShowTimeInventoryResponse>> getShowTimeInventories(String secret, UUID showTimeId) {
        log.info("[EventServiceFallback] Lấy tồn kho cho showTimeId={}", showTimeId);
        List<ShowTimeInventoryResponse> list = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<String, MockInventoryItem> entry : inventoryStore.entrySet()) {
                if (entry.getKey().startsWith(showTimeId.toString() + ":")) {
                    MockInventoryItem item = entry.getValue();
                    list.add(ShowTimeInventoryResponse.builder()
                            .id(UUID.randomUUID())
                            .showTimeId(showTimeId)
                            .ticketTypeId(item.ticketTypeId)
                            .ticketTypeName(item.ticketTypeName)
                            .price(item.price)
                            .totalQuantity(item.totalQuantity)
                            .reservedQuantity(item.reservedQuantity)
                            .soldQuantity(item.soldQuantity)
                            .availableQuantity(item.getAvailableQuantity())
                            .build());
                }
            }
        }
        return ApiResponse.ok("Lấy danh sách tồn kho vé thành công", list);
    }

    @Override
    public ApiResponse<Void> releaseSoldInventory(String secret, ReleaseInventoryRequest request) {
        log.info("[EventServiceFallback] Nhả tồn kho vé đã bán cho showTimeId={}", request.getShowTimeId());
        if (request.getItems() != null) {
            synchronized (this) {
                for (TicketItemReservationRequest itemReq : request.getItems()) {
                    String key = buildKey(request.getShowTimeId(), itemReq.getTicketTypeId());
                    MockInventoryItem item = inventoryStore.get(key);
                    if (item != null) {
                        item.soldQuantity = Math.max(0, item.soldQuantity - itemReq.getQuantity());
                    }
                }
            }
        }
        return ApiResponse.ok(null);
    }

    @Override
    public ApiResponse<InternalEventSnapshotResponse> getEventSnapshot(String secret, UUID eventId) {
        log.info("[EventServiceFallback] getEventSnapshot for eventId={}", eventId);
        return ApiResponse.ok(InternalEventSnapshotResponse.builder()
                .id(eventId)
                .title("Mock Event")
                .status("PUBLISHED")
                .refundPolicy("ALLOW_REFUND")
                .refundDeadlineDays(1)
                .refundDeadlineHours(24)
                .refundFeePercent(new BigDecimal("10.00"))
                .nextShowTime(java.time.Instant.now().plus(java.time.Duration.ofDays(7)))
                .build());
    }

    @Override
    public ApiResponse<ShowTimeResponse> getShowTime(String secret, UUID showTimeId) {
        log.info("[EventServiceFallback] getShowTime for showTimeId={}", showTimeId);
        return ApiResponse.ok(ShowTimeResponse.builder()
                .id(showTimeId)
                .startTime(java.time.Instant.now().plus(java.time.Duration.ofDays(7)))
                .endTime(java.time.Instant.now().plus(java.time.Duration.ofDays(7)).plus(java.time.Duration.ofHours(3)))
                .status("SCHEDULED")
                .build());
    }
}
