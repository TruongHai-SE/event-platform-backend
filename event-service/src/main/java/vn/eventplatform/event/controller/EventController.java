package vn.eventplatform.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.dto.EventDto;
import vn.eventplatform.event.security.SecurityUtils;
import vn.eventplatform.event.service.EventService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event Management API", description = "Quản lý sự kiện, lịch diễn, loại vé, khu vực ghế ngồi và phê duyệt")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Tạo mới sự kiện ở trạng thái DRAFT")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> createDraftEvent(
            Authentication authentication,
            @Valid @RequestBody EventDto.CreateEventRequest request
    ) {
        UUID organizerId = SecurityUtils.extractUserId(authentication);
        EventDto.EventDetailResponse data = eventService.createDraftEvent(organizerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo bản nháp sự kiện thành công", data));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer/Admin] Danh sách sự kiện quản lý")
    public ResponseEntity<ApiResponse<PageResponse<EventDto.EventSummaryResponse>>> listManagedEvents(
            @RequestParam(value = "organizerId", required = false) UUID organizerId,
            @RequestParam(value = "status", required = false) EventStatus status,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            Authentication authentication,
            @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        UUID effectiveOrganizerId = isAdmin ? organizerId : actorId;
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        Page<EventDto.EventSummaryResponse> page = eventService.listManagedEvents(
                effectiveOrganizerId, isAdmin, status, categoryId, keyword, capped
        );
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer/Admin] Xem chi tiết sự kiện quản lý")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> getManagedEventDetail(
            @PathVariable("eventId") UUID eventId,
            @RequestParam(value = "showTimeId", required = false) UUID showTimeId,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                eventService.getManagedEventDetail(eventId, actorId, isAdmin, showTimeId)
        ));
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Cập nhật thông tin sự kiện ở trạng thái DRAFT/REJECTED")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> updateEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.UpdateEventRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cập nhật sự kiện thành công",
                eventService.updateEvent(eventId, actorId, isAdmin, request)
        ));
    }

    @PatchMapping("/{eventId}/refund-policy")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Cấu hình chính sách hoàn vé cho sự kiện")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> updateRefundPolicy(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.UpdateRefundPolicyRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cập nhật chính sách hoàn vé thành công",
                eventService.updateRefundPolicy(eventId, actorId, isAdmin, request)
        ));
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Xóa sự kiện ở trạng thái DRAFT/REJECTED")
    public ResponseEntity<ApiResponse<Void>> deleteDraftEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        eventService.deleteDraftEvent(eventId, actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa sự kiện thành công", null));
    }

    @PostMapping(value = "/{eventId}/media/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Upload ảnh Banner cho sự kiện (Cloudinary/Local folder isolated)")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> uploadBanner(
            @PathVariable("eventId") UUID eventId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Tải ảnh banner sự kiện thành công",
                eventService.uploadBanner(eventId, actorId, isAdmin, file)
        ));
    }

    @PostMapping(value = "/{eventId}/media/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Upload ảnh Thumbnail cho sự kiện (Cloudinary/Local folder isolated)")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> uploadThumbnail(
            @PathVariable("eventId") UUID eventId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Tải ảnh thumbnail sự kiện thành công",
                eventService.uploadThumbnail(eventId, actorId, isAdmin, file)
        ));
    }

    @PutMapping("/{eventId}/media/urls")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Cập nhật URL ảnh Banner/Thumbnail bên ngoài (HTTPS, chống SSRF)")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> updateMediaUrls(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.EventMediaUrlRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cập nhật URL hình ảnh sự kiện thành công",
                eventService.updateMediaUrls(eventId, actorId, isAdmin, request)
        ));
    }

    @PostMapping("/{eventId}/show-times")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Thêm suất diễn (ShowTime) cho sự kiện")
    public ResponseEntity<ApiResponse<EventDto.ShowTimeResponse>> addShowTime(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.CreateShowTimeRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        EventDto.ShowTimeResponse data = eventService.addShowTime(eventId, actorId, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm suất diễn thành công", data));
    }

    @PutMapping("/{eventId}/show-times/{showTimeId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Cập nhật suất diễn (ShowTime)")
    public ResponseEntity<ApiResponse<EventDto.ShowTimeResponse>> updateShowTime(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("showTimeId") UUID showTimeId,
            Authentication authentication,
            @Valid @RequestBody EventDto.UpdateShowTimeRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cập nhật suất diễn thành công",
                eventService.updateShowTime(eventId, showTimeId, actorId, isAdmin, request)
        ));
    }

    @DeleteMapping("/{eventId}/show-times/{showTimeId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Xóa suất diễn (ShowTime)")
    public ResponseEntity<ApiResponse<Void>> deleteShowTime(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("showTimeId") UUID showTimeId,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        eventService.deleteShowTime(eventId, showTimeId, actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa suất diễn", null));
    }

    @PostMapping("/{eventId}/ticket-types")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Thêm loại vé (TicketType) và tự động đồng bộ tồn kho cho các suất diễn")
    public ResponseEntity<ApiResponse<EventDto.TicketTypeResponse>> addTicketType(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.CreateTicketTypeRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        EventDto.TicketTypeResponse data = eventService.addTicketType(eventId, actorId, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm loại vé thành công", data));
    }

    @PutMapping("/{eventId}/ticket-types/{ticketTypeId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Cập nhật loại vé (TicketType)")
    public ResponseEntity<ApiResponse<EventDto.TicketTypeResponse>> updateTicketType(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("ticketTypeId") UUID ticketTypeId,
            Authentication authentication,
            @Valid @RequestBody EventDto.UpdateTicketTypeRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cập nhật loại vé thành công",
                eventService.updateTicketType(eventId, ticketTypeId, actorId, isAdmin, request)
        ));
    }

    @DeleteMapping("/{eventId}/ticket-types/{ticketTypeId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Xóa loại vé (TicketType)")
    public ResponseEntity<ApiResponse<Void>> deleteTicketType(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("ticketTypeId") UUID ticketTypeId,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        eventService.deleteTicketType(eventId, ticketTypeId, actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa loại vé", null));
    }

    @PostMapping("/{eventId}/seat-sections")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Thêm khu vực ghế ngồi (SeatSection)")
    public ResponseEntity<ApiResponse<EventDto.SeatSectionResponse>> addSeatSection(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.CreateSeatSectionRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        EventDto.SeatSectionResponse data = eventService.addSeatSection(eventId, actorId, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm khu vực ghế thành công", data));
    }

    @PutMapping("/{eventId}/seat-sections/{sectionId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Cập nhật khu vực ghế ngồi (SeatSection)")
    public ResponseEntity<ApiResponse<EventDto.SeatSectionResponse>> updateSeatSection(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("sectionId") UUID sectionId,
            Authentication authentication,
            @Valid @RequestBody EventDto.UpdateSeatSectionRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cập nhật khu vực ghế thành công",
                eventService.updateSeatSection(eventId, sectionId, actorId, isAdmin, request)
        ));
    }

    @DeleteMapping("/{eventId}/seat-sections/{sectionId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Xóa khu vực ghế ngồi")
    public ResponseEntity<ApiResponse<Void>> deleteSeatSection(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("sectionId") UUID sectionId,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        eventService.deleteSeatSection(eventId, sectionId, actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa khu vực ghế", null));
    }

    @PostMapping("/{eventId}/seat-sections/{sectionId}/seats")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Tạo danh sách ghế ngồi hàng loạt cho khu vực")
    public ResponseEntity<ApiResponse<List<EventDto.SeatResponse>>> bulkCreateSeats(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("sectionId") UUID sectionId,
            Authentication authentication,
            @Valid @RequestBody EventDto.BulkCreateSeatsRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        List<EventDto.SeatResponse> data = eventService.bulkCreateSeats(eventId, sectionId, actorId, isAdmin, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo danh sách ghế thành công", data));
    }

    @PutMapping("/{eventId}/seat-sections/{sectionId}/assign-ticket-type")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Gắn loại vé (TicketType) cho toàn bộ khu vực hoặc các ghế chỉ định")
    public ResponseEntity<ApiResponse<EventDto.SeatSectionResponse>> assignTicketType(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("sectionId") UUID sectionId,
            Authentication authentication,
            @Valid @RequestBody EventDto.AssignTicketTypeRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Gắn loại vé cho khu vực/ghế thành công",
                eventService.assignTicketTypeToSectionOrSeats(eventId, sectionId, actorId, isAdmin, request)
        ));
    }

    @PostMapping("/{eventId}/submit")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Gửi sự kiện chờ Admin phê duyệt (DRAFT/REJECTED -> PENDING_REVIEW)")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> submitEventForReview(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Đã gửi sự kiện chờ phê duyệt",
                eventService.submitEventForReview(eventId, actorId, isAdmin)
        ));
    }

    @PostMapping("/{eventId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Phê duyệt và xuất bản sự kiện (PENDING_REVIEW -> PUBLISHED)")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> approveEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication
    ) {
        UUID adminId = SecurityUtils.extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Sự kiện đã được phê duyệt và công bố",
                eventService.approveEvent(eventId, adminId)
        ));
    }

    @PostMapping("/{eventId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Từ chối phê duyệt sự kiện kèm lý do (PENDING_REVIEW -> REJECTED)")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> rejectEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.RejectEventRequest request
    ) {
        UUID adminId = SecurityUtils.extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Đã từ chối phê duyệt sự kiện",
                eventService.rejectEvent(eventId, adminId, request)
        ));
    }

    @PostMapping("/{eventId}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer/Admin] Hủy sự kiện và kích hoạt hủy vé hàng loạt")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> cancelEvent(
            @PathVariable("eventId") UUID eventId,
            Authentication authentication,
            @Valid @RequestBody EventDto.CancelEventRequest request
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                "Sự kiện đã được hủy thành công",
                eventService.cancelEvent(eventId, actorId, isAdmin, request)
        ));
    }

    @PostMapping("/mark-ended")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Chuyển các sự kiện đã qua toàn bộ suất diễn sang trạng thái ENDED")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markEndedEvents() {
        int count = eventService.markEndedEventsNow();
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật các sự kiện kết thúc", Map.of("endedCount", count)));
    }
}
