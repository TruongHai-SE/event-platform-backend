package vn.eventplatform.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.event.client.TicketServiceClient;
import vn.eventplatform.event.domain.Category;
import vn.eventplatform.event.domain.Event;
import vn.eventplatform.event.domain.OutboxEvent;
import vn.eventplatform.event.domain.Seat;
import vn.eventplatform.event.domain.SeatAvailability;
import vn.eventplatform.event.domain.SeatSection;
import vn.eventplatform.event.domain.ShowTime;
import vn.eventplatform.event.domain.ShowTimeTicketInventory;
import vn.eventplatform.event.domain.TicketType;
import vn.eventplatform.event.domain.enums.CategoryStatus;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.domain.enums.RefundPolicy;
import vn.eventplatform.event.domain.enums.SeatAvailabilityStatus;
import vn.eventplatform.event.domain.enums.SeatingMode;
import vn.eventplatform.event.domain.enums.ShowTimeStatus;
import vn.eventplatform.event.dto.EventDto;
import vn.eventplatform.event.mapper.EventMapper;
import vn.eventplatform.event.repository.CategoryRepository;
import vn.eventplatform.event.repository.EventRepository;
import vn.eventplatform.event.repository.OutboxEventRepository;
import vn.eventplatform.event.repository.SeatAvailabilityRepository;
import vn.eventplatform.event.repository.SeatRepository;
import vn.eventplatform.event.repository.SeatSectionRepository;
import vn.eventplatform.event.repository.ShowTimeRepository;
import vn.eventplatform.event.repository.ShowTimeTicketInventoryRepository;
import vn.eventplatform.event.repository.TicketTypeRepository;
import vn.eventplatform.event.util.NormalizationUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final ShowTimeRepository showTimeRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ShowTimeTicketInventoryRepository inventoryRepository;
    private final SeatSectionRepository seatSectionRepository;
    private final SeatRepository seatRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EventMediaService eventMediaService;
    private final TicketServiceClient ticketServiceClient;
    private final EventMapper eventMapper;
    private final ObjectMapper objectMapper;

    @Value("${security.internal.secret:internal-secret-key}")
    private String internalSecret;

    @Transactional
    public EventDto.EventDetailResponse createDraftEvent(UUID organizerId, EventDto.CreateEventRequest request) {
        Category category = getActiveCategoryOrThrow(request.getCategoryId());
        String cleanTitle = NormalizationUtils.cleanDisplayText(request.getTitle());
        if (cleanTitle.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tiêu đề sự kiện không hợp lệ");
        }

        RefundPolicy policy = request.getRefundPolicy() != null ? request.getRefundPolicy() : RefundPolicy.NO_REFUND;
        Integer deadlineDays = request.getRefundDeadlineHours() != null ? request.getRefundDeadlineHours() : 0;
        BigDecimal feePercent = request.getRefundFeePercent() != null ? request.getRefundFeePercent() : BigDecimal.ZERO;
        validateRefundPolicy(policy, deadlineDays, feePercent);
        if (policy == RefundPolicy.NO_REFUND) {
            deadlineDays = 0;
            feePercent = BigDecimal.ZERO;
        }

        String combinedVenueAddress = buildCombinedVenueAddress(request.getAddress(), request.getCity());

        Event event = Event.builder()
                .organizerId(organizerId)
                .category(category)
                .title(cleanTitle)
                .slug(generateUniqueEventSlug(cleanTitle, null))
                .description(request.getDescription())
                .status(EventStatus.DRAFT)
                .seatingMode(request.getSeatingMode() != null ? request.getSeatingMode() : SeatingMode.GENERAL_ADMISSION)
                .venueName(request.getVenueName())
                .venueAddress(combinedVenueAddress)
                .refundPolicy(policy)
                .refundDeadlineDays(deadlineDays)
                .refundFeePercentage(feePercent)
                .build();

        event = eventRepository.saveAndFlush(event);
        return buildEventDetailResponse(event, null);
    }

    @Transactional
    public EventDto.EventDetailResponse updateEvent(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.UpdateEventRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        Category category = getActiveCategoryOrThrow(request.getCategoryId());
        String cleanTitle = NormalizationUtils.cleanDisplayText(request.getTitle());
        if (cleanTitle.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tiêu đề sự kiện không hợp lệ");
        }

        RefundPolicy policy = request.getRefundPolicy() != null ? request.getRefundPolicy() : event.getRefundPolicy();
        Integer deadlineDays = request.getRefundDeadlineHours() != null
                ? request.getRefundDeadlineHours()
                : event.getRefundDeadlineDays();
        BigDecimal feePercent = request.getRefundFeePercent() != null
                ? request.getRefundFeePercent()
                : event.getRefundFeePercentage();
        validateRefundPolicy(policy, deadlineDays, feePercent);
        if (policy == RefundPolicy.NO_REFUND) {
            deadlineDays = 0;
            feePercent = BigDecimal.ZERO;
        }

        event.setCategory(category);
        event.setTitle(cleanTitle);
        event.setSlug(generateUniqueEventSlug(cleanTitle, event.getId()));
        event.setDescription(request.getDescription());
        if (request.getSeatingMode() != null) {
            event.setSeatingMode(request.getSeatingMode());
        }
        event.setVenueName(request.getVenueName());
        event.setVenueAddress(buildCombinedVenueAddress(request.getAddress(), request.getCity()));
        event.setRefundPolicy(policy);
        event.setRefundDeadlineDays(deadlineDays);
        event.setRefundFeePercentage(feePercent);

        event = eventRepository.saveAndFlush(event);
        return buildEventDetailResponse(event, null);
    }

    @Transactional
    public EventDto.EventDetailResponse updateRefundPolicy(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.UpdateRefundPolicyRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        RefundPolicy policy = request.getRefundPolicy();
        Integer deadlineDays = request.getRefundDeadlineHours() != null ? request.getRefundDeadlineHours() : 0;
        BigDecimal feePercent = request.getRefundFeePercent() != null ? request.getRefundFeePercent() : BigDecimal.ZERO;
        validateRefundPolicy(policy, deadlineDays, feePercent);

        if (policy == RefundPolicy.NO_REFUND) {
            deadlineDays = 0;
            feePercent = BigDecimal.ZERO;
        }

        event.setRefundPolicy(policy);
        event.setRefundDeadlineDays(deadlineDays);
        event.setRefundFeePercentage(feePercent);

        return buildEventDetailResponse(eventRepository.saveAndFlush(event), null);
    }

    @Transactional
    public void deleteDraftEvent(UUID eventId, UUID actorId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.REJECTED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Chỉ được phép xóa sự kiện ở trạng thái DRAFT hoặc REJECTED (trạng thái hiện tại: " + event.getStatus() + ")");
        }

        if (StringUtils.hasText(event.getBannerPublicId())) {
            eventMediaService.deleteMediaAssetSafely(event.getBannerPublicId());
        }
        if (StringUtils.hasText(event.getThumbnailPublicId())) {
            eventMediaService.deleteMediaAssetSafely(event.getThumbnailPublicId());
        }
        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(eventId);
        for (ShowTime st : showTimes) {
            seatAvailabilityRepository.deleteAll(seatAvailabilityRepository.findByShowTimeId(st.getId()));
            inventoryRepository.deleteAll(inventoryRepository.findByShowTimeId(st.getId()));
        }
        seatRepository.deleteAll(seatRepository.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId));
        seatSectionRepository.deleteAll(seatSectionRepository.findByEventIdOrderBySortOrderAscNameAsc(eventId));
        ticketTypeRepository.deleteAll(ticketTypeRepository.findByEventIdOrderByPriceAsc(eventId));
        showTimeRepository.deleteAll(showTimes);
        eventRepository.delete(event);
    }

    @Transactional
    public EventDto.EventDetailResponse uploadBanner(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            MultipartFile file
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        String oldPublicId = event.getBannerPublicId();
        EventMediaService.MediaUploadResult uploaded =
                eventMediaService.uploadEventImage(eventId, file, "banners", "banner");
        if (StringUtils.hasText(oldPublicId) && !oldPublicId.equals(uploaded.publicId())) {
            eventMediaService.deleteMediaAssetSafely(oldPublicId);
        }

        event.setBannerUrl(uploaded.url());
        event.setBannerPublicId(uploaded.publicId());
        return buildEventDetailResponse(eventRepository.saveAndFlush(event), null);
    }

    @Transactional
    public EventDto.EventDetailResponse uploadThumbnail(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            MultipartFile file
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        String oldPublicId = event.getThumbnailPublicId();
        EventMediaService.MediaUploadResult uploaded =
                eventMediaService.uploadEventImage(eventId, file, "thumbnails", "thumb");
        if (StringUtils.hasText(oldPublicId) && !oldPublicId.equals(uploaded.publicId())) {
            eventMediaService.deleteMediaAssetSafely(oldPublicId);
        }

        event.setThumbnailUrl(uploaded.url());
        event.setThumbnailPublicId(uploaded.publicId());
        return buildEventDetailResponse(eventRepository.saveAndFlush(event), null);
    }

    @Transactional
    public EventDto.EventDetailResponse updateMediaUrls(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.EventMediaUrlRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        if (StringUtils.hasText(request.getBannerUrl())) {
            String validBanner = eventMediaService.validateExternalImageUrl(request.getBannerUrl());
            if (StringUtils.hasText(event.getBannerPublicId())) {
                eventMediaService.deleteMediaAssetSafely(event.getBannerPublicId());
                event.setBannerPublicId(null);
            }
            event.setBannerUrl(validBanner);
        }
        if (StringUtils.hasText(request.getThumbnailUrl())) {
            String validThumb = eventMediaService.validateExternalImageUrl(request.getThumbnailUrl());
            if (StringUtils.hasText(event.getThumbnailPublicId())) {
                eventMediaService.deleteMediaAssetSafely(event.getThumbnailPublicId());
                event.setThumbnailPublicId(null);
            }
            event.setThumbnailUrl(validThumb);
        }
        return buildEventDetailResponse(eventRepository.saveAndFlush(event), null);
    }

    @Transactional
    public EventDto.ShowTimeResponse addShowTime(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.CreateShowTimeRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);
        validateShowTimeWindow(request.getStartTime(), request.getEndTime());

        if (showTimeRepository.existsOverlappingShowTime(eventId, null, request.getStartTime(), request.getEndTime())) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Khung giờ suất diễn bị trùng lặp (overlap) với một suất diễn khác của sự kiện này");
        }

        ShowTime showTime = ShowTime.builder()
                .event(event)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(ShowTimeStatus.SCHEDULED)
                .build();
        showTime = showTimeRepository.saveAndFlush(showTime);

        List<TicketType> ticketTypes = ticketTypeRepository.findByEventIdOrderByPriceAsc(eventId);
        List<ShowTimeTicketInventory> inventories = new ArrayList<>();
        for (TicketType tt : ticketTypes) {
            ShowTimeTicketInventory inv = ShowTimeTicketInventory.builder()
                    .showTime(showTime)
                    .ticketType(tt)
                    .totalQuantity(tt.getTotalQuantity())
                    .reservedQuantity(0)
                    .soldQuantity(0)
                    .build();
            inventories.add(inventoryRepository.save(inv));
        }

        if (event.getSeatingMode() == SeatingMode.ASSIGNED_SEAT) {
            List<Seat> seats = seatRepository.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId);
            for (Seat seat : seats) {
                if (!seatAvailabilityRepository.existsByShowTimeIdAndSeatId(showTime.getId(), seat.getId())) {
                    seatAvailabilityRepository.save(SeatAvailability.builder()
                            .showTime(showTime)
                            .seat(seat)
                            .status(SeatAvailabilityStatus.AVAILABLE)
                            .build());
                }
            }
        }

        EventDto.ShowTimeResponse response = eventMapper.toShowTimeResponse(showTime);
        response.setInventories(inventories.stream().map(eventMapper::toTicketInventoryResponse).toList());
        return response;
    }

    @Transactional
    public EventDto.ShowTimeResponse updateShowTime(
            UUID eventId,
            UUID showTimeId,
            UUID actorId,
            boolean isAdmin,
            EventDto.UpdateShowTimeRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);
        validateShowTimeWindow(request.getStartTime(), request.getEndTime());

        ShowTime showTime = showTimeRepository.findByIdAndEventId(showTimeId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy suất diễn"));

        if (showTimeRepository.existsOverlappingShowTime(eventId, showTimeId, request.getStartTime(), request.getEndTime())) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Khung giờ suất diễn bị trùng lặp (overlap) với một suất diễn khác của sự kiện này");
        }

        showTime.setStartTime(request.getStartTime());
        showTime.setEndTime(request.getEndTime());
        if (request.getStatus() != null) {
            showTime.setStatus(request.getStatus());
        }
        showTime = showTimeRepository.saveAndFlush(showTime);

        List<ShowTimeTicketInventory> inventories = inventoryRepository.findByShowTimeIdWithTicketType(showTimeId);
        EventDto.ShowTimeResponse response = eventMapper.toShowTimeResponse(showTime);
        response.setInventories(inventories.stream().map(eventMapper::toTicketInventoryResponse).toList());
        return response;
    }

    @Transactional
    public void deleteShowTime(UUID eventId, UUID showTimeId, UUID actorId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        ShowTime showTime = showTimeRepository.findByIdAndEventId(showTimeId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy suất diễn"));

        if (inventoryRepository.hasActiveReservationsOrSalesByShowTime(showTimeId)) {
            throw new AppException(ErrorCode.CONFLICT, "Không thể xóa suất diễn đã có vé đặt hoặc bán");
        }
        seatAvailabilityRepository.deleteAll(seatAvailabilityRepository.findByShowTimeId(showTimeId));
        inventoryRepository.deleteAll(inventoryRepository.findByShowTimeId(showTimeId));
        showTimeRepository.delete(showTime);
    }

    @Transactional
    public EventDto.TicketTypeResponse addTicketType(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.CreateTicketTypeRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        int maxPerOrder = request.getMaxPerOrder() != null ? request.getMaxPerOrder() : Math.min(4, request.getTotalQuantity());
        validateTicketTypeRequest(request.getPrice(), request.getTotalQuantity(), maxPerOrder,
                request.getSaleStartTime(), request.getSaleEndTime());

        TicketType ticketType = TicketType.builder()
                .event(event)
                .name(NormalizationUtils.cleanDisplayText(request.getName()))
                .description(request.getDescription())
                .price(request.getPrice())
                .totalQuantity(request.getTotalQuantity())
                .maxPerOrder(maxPerOrder)
                .saleStartTime(request.getSaleStartTime())
                .saleEndTime(request.getSaleEndTime())
                .build();
        ticketType = ticketTypeRepository.saveAndFlush(ticketType);

        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(eventId);
        for (ShowTime st : showTimes) {
            if (!inventoryRepository.existsByShowTimeIdAndTicketTypeId(st.getId(), ticketType.getId())) {
                inventoryRepository.save(ShowTimeTicketInventory.builder()
                        .showTime(st)
                        .ticketType(ticketType)
                        .totalQuantity(ticketType.getTotalQuantity())
                        .reservedQuantity(0)
                        .soldQuantity(0)
                        .build());
            }
        }
        return eventMapper.toTicketTypeResponse(ticketType);
    }

    @Transactional
    public EventDto.TicketTypeResponse updateTicketType(
            UUID eventId,
            UUID ticketTypeId,
            UUID actorId,
            boolean isAdmin,
            EventDto.UpdateTicketTypeRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy loại vé"));

        int maxPerOrder = request.getMaxPerOrder() != null ? request.getMaxPerOrder() : ticketType.getMaxPerOrder();
        validateTicketTypeRequest(request.getPrice(), request.getTotalQuantity(), maxPerOrder,
                request.getSaleStartTime(), request.getSaleEndTime());

        List<ShowTimeTicketInventory> inventories = inventoryRepository.findByTicketTypeId(ticketTypeId);
        for (ShowTimeTicketInventory inv : inventories) {
            int committed = inv.getReservedQuantity() + inv.getSoldQuantity();
            if (request.getTotalQuantity() < committed) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Tổng số lượng vé không được nhỏ hơn số lượng vé đã giữ chỗ/bán (" + committed + ")");
            }
            inv.setTotalQuantity(request.getTotalQuantity());
            inventoryRepository.save(inv);
        }

        ticketType.setName(NormalizationUtils.cleanDisplayText(request.getName()));
        ticketType.setDescription(request.getDescription());
        ticketType.setPrice(request.getPrice());
        ticketType.setTotalQuantity(request.getTotalQuantity());
        ticketType.setMaxPerOrder(maxPerOrder);
        ticketType.setSaleStartTime(request.getSaleStartTime());
        ticketType.setSaleEndTime(request.getSaleEndTime());

        return eventMapper.toTicketTypeResponse(ticketTypeRepository.saveAndFlush(ticketType));
    }

    @Transactional
    public void deleteTicketType(UUID eventId, UUID ticketTypeId, UUID actorId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy loại vé"));

        if (inventoryRepository.hasActiveReservationsOrSalesByTicketType(ticketTypeId)) {
            throw new AppException(ErrorCode.CONFLICT, "Không thể xóa loại vé đã có người đặt hoặc mua");
        }
        List<Seat> seats = seatRepository.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId);
        for (Seat s : seats) {
            if (s.getTicketType() != null && ticketTypeId.equals(s.getTicketType().getId())) {
                s.setTicketType(null);
                seatRepository.save(s);
            }
        }
        inventoryRepository.deleteAll(inventoryRepository.findByTicketTypeId(ticketTypeId));
        ticketTypeRepository.delete(ticketType);
    }

    @Transactional
    public EventDto.SeatSectionResponse addSeatSection(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.CreateSeatSectionRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);
        ensureAssignedSeatMode(event);

        String code = request.getCode().trim().toUpperCase();
        if (seatSectionRepository.existsByEventIdAndCodeIgnoreCase(eventId, code)) {
            throw new AppException(ErrorCode.CONFLICT, "Mã khu vực ghế '" + code + "' đã tồn tại trong sự kiện");
        }

        String metadataTicketTypeId = null;
        if (request.getAssignedTicketTypeId() != null) {
            TicketType assignedType = ticketTypeRepository.findByIdAndEventId(request.getAssignedTicketTypeId(), eventId)
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Loại vé gắn cho khu vực không thuộc sự kiện này"));
            metadataTicketTypeId = assignedType.getId().toString();
        }

        SeatSection section = SeatSection.builder()
                .event(event)
                .code(code)
                .name(NormalizationUtils.cleanDisplayText(request.getName()))
                .capacity(request.getCapacity())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .metadata(metadataTicketTypeId)
                .build();
        section = seatSectionRepository.saveAndFlush(section);

        EventDto.SeatSectionResponse response = eventMapper.toSeatSectionResponse(section);
        response.setAssignedTicketTypeId(parseUuidSafely(section.getMetadata()));
        response.setSeats(List.of());
        return response;
    }

    @Transactional
    public EventDto.SeatSectionResponse updateSeatSection(
            UUID eventId,
            UUID sectionId,
            UUID actorId,
            boolean isAdmin,
            EventDto.UpdateSeatSectionRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);
        ensureAssignedSeatMode(event);

        SeatSection section = seatSectionRepository.findByIdAndEventId(sectionId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khu vực ghế"));

        String code = request.getCode().trim().toUpperCase();
        if (seatSectionRepository.existsByEventIdAndCodeIgnoreCaseAndIdNot(eventId, code, sectionId)) {
            throw new AppException(ErrorCode.CONFLICT, "Mã khu vực ghế '" + code + "' đã tồn tại trong sự kiện");
        }

        long existingSeatsCount = seatRepository.countBySectionId(sectionId);
        if (request.getCapacity() < existingSeatsCount) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Sức chứa khu vực không được nhỏ hơn số ghế hiện có (" + existingSeatsCount + ")");
        }

        if (request.getAssignedTicketTypeId() != null) {
            TicketType assignedType = ticketTypeRepository.findByIdAndEventId(request.getAssignedTicketTypeId(), eventId)
                    .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Loại vé gắn cho khu vực không thuộc sự kiện này"));
            section.setMetadata(assignedType.getId().toString());
        }

        section.setCode(code);
        section.setName(NormalizationUtils.cleanDisplayText(request.getName()));
        section.setCapacity(request.getCapacity());
        if (request.getSortOrder() != null) {
            section.setSortOrder(request.getSortOrder());
        }
        section = seatSectionRepository.saveAndFlush(section);

        List<Seat> seats = seatRepository.findBySectionIdOrderByRowLabelAscSeatNumberAsc(sectionId);
        EventDto.SeatSectionResponse response = eventMapper.toSeatSectionResponse(section);
        response.setAssignedTicketTypeId(parseUuidSafely(section.getMetadata()));
        response.setSeats(seats.stream().map(eventMapper::toSeatResponse).toList());
        return response;
    }

    @Transactional
    public void deleteSeatSection(UUID eventId, UUID sectionId, UUID actorId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);

        SeatSection section = seatSectionRepository.findByIdAndEventId(sectionId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khu vực ghế"));
        seatSectionRepository.delete(section);
    }

    @Transactional
    public List<EventDto.SeatResponse> bulkCreateSeats(
            UUID eventId,
            UUID sectionId,
            UUID actorId,
            boolean isAdmin,
            EventDto.BulkCreateSeatsRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);
        ensureAssignedSeatMode(event);

        SeatSection section = seatSectionRepository.findByIdAndEventId(sectionId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khu vực ghế"));

        long currentSeatCount = seatRepository.countBySectionId(sectionId);
        if (currentSeatCount + request.getSeats().size() > section.getCapacity()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tổng số ghế vượt quá sức chứa của khu vực (" + section.getCapacity() + ")");
        }

        UUID defaultSectionTicketTypeId = parseUuidSafely(section.getMetadata());
        TicketType defaultTicketType = defaultSectionTicketTypeId != null
                ? ticketTypeRepository.findByIdAndEventId(defaultSectionTicketTypeId, eventId).orElse(null)
                : null;

        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(eventId);
        List<Seat> createdSeats = new ArrayList<>();
        Set<String> seenInBatch = new HashSet<>();

        for (EventDto.SeatItemRequest item : request.getSeats()) {
            String rowLabel = item.getRowLabel().trim().toUpperCase();
            String seatNumber = item.getSeatNumber().trim().toUpperCase();
            String key = rowLabel + ":" + seatNumber;
            if (!seenInBatch.add(key)) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Ghế trùng lặp trong danh sách gửi lên: Hàng " + rowLabel + " - Ghế " + seatNumber);
            }

            TicketType assignedType = defaultTicketType;
            if (item.getAssignedTicketTypeId() != null) {
                assignedType = ticketTypeRepository.findByIdAndEventId(item.getAssignedTicketTypeId(), eventId)
                        .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Loại vé gắn cho ghế không thuộc sự kiện này"));
            }

            String label = StringUtils.hasText(item.getSeatLabel())
                    ? item.getSeatLabel().trim()
                    : (section.getCode() + "-" + rowLabel + seatNumber);

            Seat seat = Seat.builder()
                    .event(event)
                    .section(section)
                    .rowLabel(rowLabel)
                    .seatNumber(seatNumber)
                    .seatLabel(label)
                    .ticketType(assignedType)
                    .build();
            try {
                seat = seatRepository.saveAndFlush(seat);
            } catch (DataIntegrityViolationException ex) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Ghế Hàng " + rowLabel + " - Số " + seatNumber + " đã tồn tại trong khu vực " + section.getCode());
            }
            createdSeats.add(seat);

            for (ShowTime st : showTimes) {
                if (!seatAvailabilityRepository.existsByShowTimeIdAndSeatId(st.getId(), seat.getId())) {
                    seatAvailabilityRepository.save(SeatAvailability.builder()
                            .showTime(st)
                            .seat(seat)
                            .status(SeatAvailabilityStatus.AVAILABLE)
                            .build());
                }
            }
        }
        return createdSeats.stream().map(eventMapper::toSeatResponse).toList();
    }

    @Transactional
    public EventDto.SeatSectionResponse assignTicketTypeToSectionOrSeats(
            UUID eventId,
            UUID sectionId,
            UUID actorId,
            boolean isAdmin,
            EventDto.AssignTicketTypeRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        ensureEditableState(event);
        ensureAssignedSeatMode(event);

        SeatSection section = seatSectionRepository.findByIdAndEventId(sectionId, eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khu vực ghế"));

        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(request.getTicketTypeId(), eventId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Loại vé không thuộc sự kiện này"));

        List<Seat> seatsInSection = seatRepository.findBySectionIdOrderByRowLabelAscSeatNumberAsc(sectionId);
        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            section.setMetadata(ticketType.getId().toString());
            seatSectionRepository.save(section);
            for (Seat seat : seatsInSection) {
                seat.setTicketType(ticketType);
                seatRepository.save(seat);
            }
        } else {
            Set<UUID> targetSeatIds = new HashSet<>(request.getSeatIds());
            for (Seat seat : seatsInSection) {
                if (targetSeatIds.contains(seat.getId())) {
                    seat.setTicketType(ticketType);
                    seatRepository.save(seat);
                }
            }
        }

        EventDto.SeatSectionResponse response = eventMapper.toSeatSectionResponse(section);
        response.setAssignedTicketTypeId(parseUuidSafely(section.getMetadata()));
        response.setSeats(seatsInSection.stream().map(eventMapper::toSeatResponse).toList());
        return response;
    }

    @Transactional
    public EventDto.EventDetailResponse submitEventForReview(UUID eventId, UUID actorId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.REJECTED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Chỉ có thể gửi duyệt sự kiện từ trạng thái DRAFT hoặc REJECTED (hiện tại: " + event.getStatus() + ")");
        }

        validateEventReadinessForSubmission(event);

        event.setStatus(EventStatus.PENDING);
        event.setRejectionReason(null);
        event = eventRepository.saveAndFlush(event);

        recordOutboxEvent("EVENT", event.getId(), "EVENT_SUBMITTED", Map.of(
                "eventId", event.getId(),
                "organizerId", event.getOrganizerId(),
                "title", event.getTitle()
        ));

        return buildEventDetailResponse(event, null);
    }

    @Transactional
    public EventDto.EventDetailResponse approveEvent(UUID eventId, UUID adminId) {
        Event event = getEventOrThrow(eventId);
        if (event.getStatus() != EventStatus.PENDING && event.getStatus() != EventStatus.APPROVED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Chỉ có thể phê duyệt sự kiện đang ở trạng thái PENDING (hiện tại: " + event.getStatus() + ")");
        }

        validateEventReadinessForSubmission(event);

        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(eventId);
        List<TicketType> ticketTypes = ticketTypeRepository.findByEventIdOrderByPriceAsc(eventId);
        for (ShowTime st : showTimes) {
            for (TicketType tt : ticketTypes) {
                if (!inventoryRepository.existsByShowTimeIdAndTicketTypeId(st.getId(), tt.getId())) {
                    inventoryRepository.save(ShowTimeTicketInventory.builder()
                            .showTime(st)
                            .ticketType(tt)
                            .totalQuantity(tt.getTotalQuantity())
                            .reservedQuantity(0)
                            .soldQuantity(0)
                            .build());
                }
            }
        }

        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setRejectionReason(null);
        event = eventRepository.saveAndFlush(event);

        recordOutboxEvent("EVENT", event.getId(), "EVENT_PUBLISHED", Map.of(
                "eventId", event.getId(),
                "organizerId", event.getOrganizerId(),
                "approvedBy", adminId,
                "publishedAt", event.getPublishedAt().toString()
        ));

        return buildEventDetailResponse(event, null);
    }

    @Transactional
    public EventDto.EventDetailResponse rejectEvent(
            UUID eventId,
            UUID adminId,
            EventDto.RejectEventRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        if (event.getStatus() != EventStatus.PENDING) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Chỉ có thể từ chối sự kiện đang ở trạng thái PENDING (hiện tại: " + event.getStatus() + ")");
        }

        event.setStatus(EventStatus.REJECTED);
        event.setRejectionReason(request.getRejectionReason().trim());
        event = eventRepository.saveAndFlush(event);

        recordOutboxEvent("EVENT", event.getId(), "EVENT_REJECTED", Map.of(
                "eventId", event.getId(),
                "organizerId", event.getOrganizerId(),
                "rejectedBy", adminId,
                "rejectionReason", event.getRejectionReason()
        ));

        return buildEventDetailResponse(event, null);
    }

    @Transactional
    public EventDto.EventDetailResponse cancelEvent(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            EventDto.CancelEventRequest request
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);

        if (event.getStatus() != EventStatus.PUBLISHED
                && event.getStatus() != EventStatus.PENDING
                && event.getStatus() != EventStatus.APPROVED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Chỉ có thể hủy sự kiện đang ở trạng thái PUBLISHED hoặc PENDING (hiện tại: " + event.getStatus() + ")");
        }

        String reason = request.getCancellationReason().trim();
        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(Instant.now());
        event.setCancellationReason(reason);

        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(eventId);
        List<UUID> showTimeIds = new ArrayList<>();
        for (ShowTime st : showTimes) {
            st.setStatus(ShowTimeStatus.CANCELLED);
            showTimeRepository.save(st);
            showTimeIds.add(st.getId());
        }

        event = eventRepository.saveAndFlush(event);

        recordOutboxEvent("EVENT", event.getId(), "EVENT_CANCELLED", Map.of(
                "eventId", event.getId(),
                "showTimeIds", showTimeIds,
                "cancellationReason", reason,
                "cancelledAt", event.getCancelledAt().toString()
        ));

        if (!showTimeIds.isEmpty()) {
            try {
                ticketServiceClient.cancelTicketsByShowTimes(
                        internalSecret,
                        TicketServiceClient.CancelTicketsRequest.builder()
                                .showTimeIds(showTimeIds)
                                .cancellationReason(reason)
                                .build()
                );
            } catch (Exception ex) {
                log.warn("Non-fatal error calling ticket-service on event cancellation (saved to Outbox): {}", ex.getMessage());
            }
        }

        return buildEventDetailResponse(event, null);
    }

    @Transactional
    public int markEndedEventsNow() {
        Instant now = Instant.now();
        List<Event> candidates = eventRepository.findPublishedEventsReadyToComplete(EventStatus.PUBLISHED, now);
        for (Event event : candidates) {
            event.setStatus(EventStatus.ENDED);
            event.setEndedAt(now);
            List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(event.getId());
            for (ShowTime st : showTimes) {
                if (st.getStatus() == ShowTimeStatus.SCHEDULED && !st.getEndTime().isAfter(now)) {
                    st.setStatus(ShowTimeStatus.ENDED);
                    showTimeRepository.save(st);
                }
            }
            eventRepository.save(event);
            recordOutboxEvent("EVENT", event.getId(), "EVENT_ENDED", Map.of(
                    "eventId", event.getId(),
                    "endedAt", now.toString()
            ));
        }
        return candidates.size();
    }

    @Transactional(readOnly = true)
    public EventDto.EventDetailResponse getManagedEventDetail(
            UUID eventId,
            UUID actorId,
            boolean isAdmin,
            UUID showTimeIdForSeatMap
    ) {
        Event event = getEventOrThrow(eventId);
        ensureOwnershipOrAdmin(event, actorId, isAdmin);
        return buildEventDetailResponse(event, showTimeIdForSeatMap);
    }

    @Transactional(readOnly = true)
    public Page<EventDto.EventSummaryResponse> listManagedEvents(
            UUID organizerId,
            boolean isAdmin,
            EventStatus status,
            UUID categoryId,
            String keyword,
            Pageable pageable
    ) {
        Page<Event> page = eventRepository.findAll(
                EventSpecification.filterManagedEvents(organizerId, status, categoryId, keyword),
                pageable
        );
        return page.map(this::enrichEventSummary);
    }

    @Transactional(readOnly = true)
    public Page<EventDto.EventSummaryResponse> searchPublicEvents(
            String keyword,
            UUID categoryId,
            String categorySlug,
            String city,
            Instant startFrom,
            Instant endTo,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    ) {
        Page<Event> page = eventRepository.findAll(
                EventSpecification.filterPublicEvents(
                        keyword, categoryId, categorySlug, city, startFrom, endTo, minPrice, maxPrice
                ),
                pageable
        );
        return page.map(this::enrichEventSummary);
    }

    @Transactional(readOnly = true)
    public EventDto.EventDetailResponse getPublicEventDetail(String idOrSlug, UUID showTimeIdForSeatMap) {
        Event event = null;
        try {
            UUID uuid = UUID.fromString(idOrSlug);
            event = eventRepository.findWithCategoryById(uuid).orElse(null);
        } catch (IllegalArgumentException ignored) {
        }
        if (event == null) {
            event = eventRepository.findWithCategoryBySlug(idOrSlug)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện"));
        }

        if (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.ENDED) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện công khai");
        }
        return buildEventDetailResponse(event, showTimeIdForSeatMap);
    }

    @Transactional(readOnly = true)
    public List<EventDto.TicketInventoryResponse> getPublicShowTimeInventories(UUID showTimeId) {
        ShowTime showTime = showTimeRepository.findById(showTimeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy suất diễn"));

        Event event = showTime.getEvent();
        if (event == null || (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.ENDED)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện công khai");
        }

        if (showTime.getStatus() == ShowTimeStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Suất diễn đã bị hủy");
        }

        List<ShowTimeTicketInventory> inventories = inventoryRepository.findByShowTimeIdWithTicketType(showTimeId);
        return inventories.stream()
                .map(eventMapper::toTicketInventoryResponse)
                .toList();
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

    private EventDto.EventSummaryResponse enrichEventSummary(Event event) {
        EventDto.EventSummaryResponse summary = eventMapper.toEventSummaryResponse(event);
        BigDecimal minPrice = event.getTicketTypes().stream()
                .map(TicketType::getPrice)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant now = Instant.now();
        Instant nextShowTime = event.getShowTimes().stream()
                .filter(st -> st.getStatus() == ShowTimeStatus.SCHEDULED && st.getStartTime().isAfter(now))
                .map(ShowTime::getStartTime)
                .min(Comparator.naturalOrder())
                .orElseGet(() -> event.getShowTimes().stream()
                        .map(ShowTime::getStartTime)
                        .min(Comparator.naturalOrder())
                        .orElse(null));
        summary.setMinPrice(minPrice);
        summary.setNextShowTime(nextShowTime);
        return summary;
    }

    private EventDto.EventDetailResponse buildEventDetailResponse(Event event, UUID showTimeIdForSeatMap) {
        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(event.getId());
        List<TicketType> ticketTypes = ticketTypeRepository.findByEventIdOrderByPriceAsc(event.getId());
        List<ShowTimeTicketInventory> allInventories = inventoryRepository.findByEventId(event.getId());

        Map<UUID, List<EventDto.TicketInventoryResponse>> invByShowTime = new HashMap<>();
        for (ShowTimeTicketInventory inv : allInventories) {
            invByShowTime.computeIfAbsent(inv.getShowTime().getId(), k -> new ArrayList<>())
                    .add(eventMapper.toTicketInventoryResponse(inv));
        }

        List<EventDto.ShowTimeResponse> showTimeDtos = new ArrayList<>();
        for (ShowTime st : showTimes) {
            EventDto.ShowTimeResponse stDto = eventMapper.toShowTimeResponse(st);
            stDto.setInventories(invByShowTime.getOrDefault(st.getId(), List.of()));
            showTimeDtos.add(stDto);
        }

        List<EventDto.TicketTypeResponse> ticketTypeDtos = ticketTypes.stream()
                .map(eventMapper::toTicketTypeResponse)
                .toList();

        UUID targetShowTimeId = showTimeIdForSeatMap != null
                ? showTimeIdForSeatMap
                : (!showTimes.isEmpty() ? showTimes.get(0).getId() : null);

        Map<UUID, SeatAvailabilityStatus> availabilityBySeat = new HashMap<>();
        if (targetShowTimeId != null) {
            for (SeatAvailability sa : seatAvailabilityRepository.findByShowTimeId(targetShowTimeId)) {
                availabilityBySeat.put(sa.getSeat().getId(), sa.getStatus());
            }
        }

        List<SeatSection> sections = seatSectionRepository.findByEventIdOrderBySortOrderAscNameAsc(event.getId());
        List<EventDto.SeatSectionResponse> sectionDtos = new ArrayList<>();
        for (SeatSection section : sections) {
            EventDto.SeatSectionResponse secDto = eventMapper.toSeatSectionResponse(section);
            UUID sectionTicketTypeId = parseUuidSafely(section.getMetadata());
            secDto.setAssignedTicketTypeId(sectionTicketTypeId);

            List<Seat> seats = seatRepository.findBySectionIdOrderByRowLabelAscSeatNumberAsc(section.getId());
            List<EventDto.SeatResponse> seatDtos = new ArrayList<>();
            for (Seat seat : seats) {
                EventDto.SeatResponse seatDto = eventMapper.toSeatResponse(seat);
                if (seatDto.getAssignedTicketTypeId() == null && sectionTicketTypeId != null) {
                    seatDto.setAssignedTicketTypeId(sectionTicketTypeId);
                }
                seatDto.setAvailabilityStatus(
                        availabilityBySeat.getOrDefault(seat.getId(), SeatAvailabilityStatus.AVAILABLE)
                );
                seatDtos.add(seatDto);
            }
            secDto.setSeats(seatDtos);
            sectionDtos.add(secDto);
        }

        return EventDto.EventDetailResponse.builder()
                .id(event.getId())
                .organizerId(event.getOrganizerId())
                .categoryId(event.getCategory().getId())
                .categoryName(event.getCategory().getName())
                .title(event.getTitle())
                .slug(event.getSlug())
                .description(event.getDescription())
                .status(event.getStatus())
                .seatingMode(event.getSeatingMode())
                .bannerUrl(event.getBannerUrl())
                .thumbnailUrl(event.getThumbnailUrl())
                .venueName(event.getVenueName())
                .city(event.getVenueAddress())
                .address(event.getVenueAddress())
                .refundPolicy(event.getRefundPolicy())
                .refundDeadlineHours(event.getRefundDeadlineDays())
                .refundFeePercent(event.getRefundFeePercentage())
                .rejectionReason(event.getRejectionReason())
                .cancellationReason(event.getCancellationReason())
                .publishedAt(event.getPublishedAt())
                .cancelledAt(event.getCancelledAt())
                .endedAt(event.getEndedAt())
                .showTimes(showTimeDtos)
                .ticketTypes(ticketTypeDtos)
                .seatSections(sectionDtos)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    private void validateEventReadinessForSubmission(Event event) {
        if (event.getCategory().getStatus() != CategoryStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh mục của sự kiện chưa được kích hoạt (PUBLISHED)");
        }
        List<ShowTime> showTimes = showTimeRepository.findByEventIdOrderByStartTimeAsc(event.getId()).stream()
                .filter(st -> st.getStatus() == ShowTimeStatus.SCHEDULED)
                .toList();
        if (showTimes.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Sự kiện phải có ít nhất 1 suất diễn (ShowTime) hợp lệ trước khi gửi duyệt");
        }
        List<TicketType> ticketTypes = ticketTypeRepository.findByEventIdOrderByPriceAsc(event.getId());
        if (ticketTypes.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Sự kiện phải có ít nhất 1 loại vé (TicketType) trước khi gửi duyệt");
        }
        if (event.getSeatingMode() == SeatingMode.ASSIGNED_SEAT) {
            List<SeatSection> sections = seatSectionRepository.findByEventIdOrderBySortOrderAscNameAsc(event.getId());
            if (sections.isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Sự kiện có ghế ngồi (ASSIGNED_SEAT) phải cấu hình ít nhất 1 khu vực ghế");
            }
            List<Seat> seats = seatRepository.findByEventIdOrderByRowLabelAscSeatNumberAsc(event.getId());
            if (seats.isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Sự kiện có ghế ngồi (ASSIGNED_SEAT) phải cấu hình danh sách ghế");
            }
            boolean allSeatsHaveTicketType = seats.stream().allMatch(
                    s -> s.getTicketType() != null || (s.getSection() != null && parseUuidSafely(s.getSection().getMetadata()) != null)
            );
            if (!allSeatsHaveTicketType) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tất cả ghế trong sự kiện ASSIGNED_SEAT phải được gắn với một loại vé (TicketType)");
            }
        }
    }

    private void validateRefundPolicy(RefundPolicy policy, Integer deadlineDays, BigDecimal feePercent) {
        if (policy == RefundPolicy.ALLOW_REFUND) {
            if (deadlineDays == null || deadlineDays < 1) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Chính sách ALLOW_REFUND yêu cầu thời hạn hoàn vé trước sự kiện >= 1");
            }
            if (feePercent != null && (feePercent.compareTo(BigDecimal.ZERO) < 0 || feePercent.compareTo(new BigDecimal("100")) > 0)) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Phí hoàn vé phải nằm trong khoảng [0, 100]");
            }
        }
    }

    private void validateShowTimeWindow(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc suất diễn phải lớn hơn thời gian bắt đầu");
        }
        if (!startTime.isAfter(Instant.now())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu suất diễn phải ở trong tương lai");
        }
    }

    private void validateTicketTypeRequest(
            BigDecimal price,
            Integer totalQuantity,
            Integer maxPerOrder,
            Instant saleStartTime,
            Instant saleEndTime
    ) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Giá vé phải >= 0");
        }
        if (totalQuantity == null || totalQuantity < 1) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tổng số lượng vé phải >= 1");
        }
        if (maxPerOrder == null || maxPerOrder < 1 || maxPerOrder > totalQuantity) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số lượng tối đa mỗi đơn phải từ 1 đến tổng số lượng vé");
        }
        if (saleStartTime != null && saleEndTime != null && !saleEndTime.isAfter(saleStartTime)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc mở bán phải sau thời gian bắt đầu mở bán");
        }
    }

    private Category getActiveCategoryOrThrow(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục sự kiện"));
        if (category.getStatus() != CategoryStatus.PUBLISHED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh mục sự kiện đang không ở trạng thái PUBLISHED");
        }
        return category;
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findWithCategoryById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện"));
    }

    private void ensureOwnershipOrAdmin(Event event, UUID actorId, boolean isAdmin) {
        if (!isAdmin && !event.getOrganizerId().equals(actorId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không phải chủ sở hữu của sự kiện này");
        }
    }

    private void ensureEditableState(Event event) {
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.REJECTED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Chỉ có thể chỉnh sửa sự kiện khi ở trạng thái DRAFT hoặc REJECTED (trạng thái hiện tại: " + event.getStatus() + ")");
        }
    }

    private void ensureAssignedSeatMode(Event event) {
        if (event.getSeatingMode() != SeatingMode.ASSIGNED_SEAT) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Sự kiện ở chế độ tự do (GENERAL_ADMISSION) không hỗ trợ cấu hình khu vực và ghế ngồi");
        }
    }

    private String buildCombinedVenueAddress(String address, String city) {
        if (StringUtils.hasText(address) && StringUtils.hasText(city)) {
            return address.trim() + ", " + city.trim();
        }
        if (StringUtils.hasText(city)) {
            return city.trim();
        }
        return StringUtils.hasText(address) ? address.trim() : null;
    }

    private UUID parseUuidSafely(String str) {
        if (!StringUtils.hasText(str)) {
            return null;
        }
        try {
            return UUID.fromString(str.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String generateUniqueEventSlug(String title, UUID excludeId) {
        String baseSlug = NormalizationUtils.toSlug(title);
        String candidate = baseSlug;
        int counter = 1;
        while (true) {
            boolean exists = (excludeId == null)
                    ? eventRepository.existsBySlug(candidate)
                    : eventRepository.existsBySlugAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
            counter++;
            candidate = baseSlug + "-" + counter;
        }
    }

    private void recordOutboxEvent(String aggregateType, UUID aggregateId, String eventType, Object payloadObj) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payloadObj);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status("PENDING")
                    .build());
        } catch (Exception ex) {
            log.error("Failed to serialize outbox payload for {}: {}", eventType, ex.getMessage());
        }
    }
}
