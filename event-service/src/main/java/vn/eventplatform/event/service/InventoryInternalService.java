package vn.eventplatform.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.event.domain.Event;
import vn.eventplatform.event.domain.ShowTime;
import vn.eventplatform.event.domain.ShowTimeTicketInventory;
import vn.eventplatform.event.domain.TicketType;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.domain.enums.InventoryUpdateStatus;
import vn.eventplatform.event.domain.enums.ShowTimeStatus;
import vn.eventplatform.event.dto.EventDto;
import vn.eventplatform.event.dto.InternalInventoryDto;
import vn.eventplatform.event.mapper.EventMapper;
import vn.eventplatform.event.repository.EventRepository;
import vn.eventplatform.event.repository.ShowTimeRepository;
import vn.eventplatform.event.repository.ShowTimeTicketInventoryRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryInternalService {

    private final EventRepository eventRepository;
    private final ShowTimeRepository showTimeRepository;
    private final ShowTimeTicketInventoryRepository inventoryRepository;
    private final EventMapper eventMapper;

    @Transactional
    public InternalInventoryDto.CheckAndReserveInventoryResponse checkAndReserve(
            InternalInventoryDto.CheckAndReserveInventoryRequest request
    ) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new AppException(ErrorCode.CONFLICT, "Sự kiện hiện không ở trạng thái mở bán (PUBLISHED)");
        }

        ShowTime showTime = showTimeRepository.findByIdAndEventId(request.getShowTimeId(), request.getEventId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Suất diễn không tồn tại hoặc không thuộc sự kiện này"));
        if (showTime.getStatus() != ShowTimeStatus.SCHEDULED || !showTime.getEndTime().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.CONFLICT, "Suất diễn đã kết thúc hoặc đã bị hủy");
        }

        // Sort ticketTypeIds deterministically to prevent deadlocks under concurrent PESSIMISTIC_WRITE locks
        Map<UUID, Integer> requestedQuantities = new TreeMap<>();
        for (InternalInventoryDto.TicketItemReservationRequest item : request.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng vé đặt phải >= 1");
            }
            requestedQuantities.merge(item.getTicketTypeId(), item.getQuantity(), Integer::sum);
        }

        List<UUID> sortedTicketTypeIds = new ArrayList<>(requestedQuantities.keySet());
        List<ShowTimeTicketInventory> lockedInventories = inventoryRepository.findByShowTimeIdAndTicketTypeIdInForUpdate(
                request.getShowTimeId(),
                sortedTicketTypeIds
        );

        Map<UUID, ShowTimeTicketInventory> inventoryMap = new LinkedHashMap<>();
        for (ShowTimeTicketInventory inv : lockedInventories) {
            inventoryMap.put(inv.getTicketType().getId(), inv);
        }

        if (inventoryMap.size() != sortedTicketTypeIds.size()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Một hoặc nhiều loại vé không tồn tại trong suất diễn này");
        }

        Instant now = Instant.now();
        List<InternalInventoryDto.TicketTypeInventoryDto> reservedItems = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : requestedQuantities.entrySet()) {
            UUID ticketTypeId = entry.getKey();
            int qty = entry.getValue();
            ShowTimeTicketInventory inv = inventoryMap.get(ticketTypeId);
            TicketType tt = inv.getTicketType();

            if (tt.getSaleStartTime() != null && now.isBefore(tt.getSaleStartTime())) {
                throw new AppException(ErrorCode.CONFLICT, "Loại vé '" + tt.getName() + "' chưa đến thời gian mở bán");
            }
            if (tt.getSaleEndTime() != null && now.isAfter(tt.getSaleEndTime())) {
                throw new AppException(ErrorCode.CONFLICT, "Loại vé '" + tt.getName() + "' đã hết thời gian mở bán");
            }
            if (qty > tt.getMaxPerOrder()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Số lượng đặt cho loại vé '" + tt.getName() + "' vượt quá giới hạn tối đa mỗi đơn (" + tt.getMaxPerOrder() + ")");
            }
            int updatedRows = inventoryRepository.reserveInventorySlot(inv.getId(), qty);
            InventoryUpdateStatus status = InventoryUpdateStatus.fromUpdatedRows(updatedRows);
            if (status != InventoryUpdateStatus.SUCCESS) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Loại vé '" + tt.getName() + "' không đủ số lượng trống (còn lại: " + inv.getAvailableQuantity() + ")");
            }

            inv.setReservedQuantity(inv.getReservedQuantity() + qty);

            reservedItems.add(InternalInventoryDto.TicketTypeInventoryDto.builder()
                    .ticketTypeId(tt.getId())
                    .ticketTypeName(tt.getName())
                    .price(tt.getPrice())
                    .maxPerOrder(tt.getMaxPerOrder())
                    .availableQuantity(inv.getAvailableQuantity())
                    .build());
        }

        return InternalInventoryDto.CheckAndReserveInventoryResponse.builder()
                .success(true)
                .reservedItems(reservedItems)
                .build();
    }

    @Transactional
    public void releaseInventory(InternalInventoryDto.ReleaseInventoryRequest request) {
        Map<UUID, Integer> releaseQuantities = new TreeMap<>();
        for (InternalInventoryDto.TicketItemReservationRequest item : request.getItems()) {
            if (item.getQuantity() != null && item.getQuantity() > 0) {
                releaseQuantities.merge(item.getTicketTypeId(), item.getQuantity(), Integer::sum);
            }
        }
        if (releaseQuantities.isEmpty()) {
            return;
        }

        List<ShowTimeTicketInventory> lockedInventories = inventoryRepository.findByShowTimeIdAndTicketTypeIdInForUpdate(
                request.getShowTimeId(),
                new ArrayList<>(releaseQuantities.keySet())
        );

        for (ShowTimeTicketInventory inv : lockedInventories) {
            int releaseQty = releaseQuantities.getOrDefault(inv.getTicketType().getId(), 0);
            if (releaseQty <= 0) {
                continue;
            }
            int updatedRows = inventoryRepository.releaseInventorySlot(inv.getId(), releaseQty);
            InventoryUpdateStatus status = InventoryUpdateStatus.fromUpdatedRows(updatedRows);
            if (status == InventoryUpdateStatus.SUCCESS) {
                inv.setReservedQuantity(inv.getReservedQuantity() - releaseQty);
            } else {
                inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - releaseQty));
                inventoryRepository.save(inv);
            }
        }
    }

    @Transactional
    public void confirmInventory(InternalInventoryDto.ConfirmInventoryRequest request) {
        Map<UUID, Integer> confirmQuantities = new TreeMap<>();
        for (InternalInventoryDto.TicketItemReservationRequest item : request.getItems()) {
            if (item.getQuantity() != null && item.getQuantity() > 0) {
                confirmQuantities.merge(item.getTicketTypeId(), item.getQuantity(), Integer::sum);
            }
        }
        if (confirmQuantities.isEmpty()) {
            return;
        }

        List<ShowTimeTicketInventory> lockedInventories = inventoryRepository.findByShowTimeIdAndTicketTypeIdInForUpdate(
                request.getShowTimeId(),
                new ArrayList<>(confirmQuantities.keySet())
        );

        for (ShowTimeTicketInventory inv : lockedInventories) {
            int confirmQty = confirmQuantities.getOrDefault(inv.getTicketType().getId(), 0);
            if (confirmQty <= 0) {
                continue;
            }
            int updatedRows = inventoryRepository.confirmInventorySlot(inv.getId(), confirmQty);
            InventoryUpdateStatus status = InventoryUpdateStatus.fromUpdatedRows(updatedRows);
            if (status != InventoryUpdateStatus.SUCCESS) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Số lượng vé giữ chỗ không đủ để xác nhận xuất vé cho loại vé " + inv.getTicketType().getName());
            }
            inv.setReservedQuantity(inv.getReservedQuantity() - confirmQty);
            inv.setSoldQuantity(inv.getSoldQuantity() + confirmQty);
        }
    }

    @Transactional(readOnly = true)
    public InternalInventoryDto.InternalEventSnapshotResponse getEventSnapshot(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện"));
        Instant nextShowTime = showTimeRepository.findByEventIdOrderByStartTimeAsc(eventId).stream()
                .filter(st -> st.getStatus() == ShowTimeStatus.SCHEDULED)
                .map(ShowTime::getStartTime)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return InternalInventoryDto.InternalEventSnapshotResponse.builder()
                .id(event.getId())
                .organizerId(event.getOrganizerId())
                .title(event.getTitle())
                .status(event.getStatus())
                .refundPolicy(event.getRefundPolicy())
                .refundDeadlineHours(event.getRefundDeadlineDays())
                .refundFeePercent(event.getRefundFeePercentage())
                .nextShowTime(nextShowTime)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EventDto.TicketInventoryResponse> getShowTimeInventories(UUID showTimeId) {
        if (!showTimeRepository.existsById(showTimeId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy suất diễn");
        }
        List<ShowTimeTicketInventory> inventories = inventoryRepository.findByShowTimeIdWithTicketType(showTimeId);
        return inventories.stream()
                .map(eventMapper::toTicketInventoryResponse)
                .toList();
    }
}
