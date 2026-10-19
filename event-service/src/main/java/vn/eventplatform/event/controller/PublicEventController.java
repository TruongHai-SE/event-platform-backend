package vn.eventplatform.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.event.dto.EventDto;
import vn.eventplatform.event.service.EventService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/public")
@RequiredArgsConstructor
@Tag(name = "Public Event Discovery API", description = "Khám phá, tìm kiếm, lọc và xem chi tiết sự kiện công khai")
public class PublicEventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Tìm kiếm, lọc và phân trang danh sách sự kiện đang mở bán (PUBLISHED)")
    public ResponseEntity<ApiResponse<PageResponse<EventDto.EventSummaryResponse>>> searchPublicEvents(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "categorySlug", required = false) String categorySlug,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "startFrom", required = false) Instant startFrom,
            @RequestParam(value = "endTo", required = false) Instant endTo,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Pageable cappedPageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), 100),
                pageable.getSort()
        );
        Page<EventDto.EventSummaryResponse> page = eventService.searchPublicEvents(
                keyword, categoryId, categorySlug, city, startFrom, endTo, minPrice, maxPrice, cappedPageable
        );
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Xem chi tiết sự kiện công khai kèm lịch diễn, loại vé và sơ đồ ghế theo ID hoặc Slug")
    public ResponseEntity<ApiResponse<EventDto.EventDetailResponse>> getPublicEventDetail(
            @PathVariable("idOrSlug") String idOrSlug,
            @RequestParam(value = "showTimeId", required = false) UUID showTimeId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.getPublicEventDetail(idOrSlug, showTimeId)));
    }

    @GetMapping("/showtimes/{showTimeId}/inventories")
    @Operation(summary = "Lấy danh sách tồn kho vé theo suất diễn công khai (gọn nhẹ phục vụ đặt vé)")
    public ResponseEntity<ApiResponse<List<EventDto.TicketInventoryResponse>>> getPublicShowTimeInventories(
            @PathVariable("showTimeId") UUID showTimeId
    ) {
        List<EventDto.TicketInventoryResponse> inventories = eventService.getPublicShowTimeInventories(showTimeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tồn kho vé thành công", inventories));
    }
}
