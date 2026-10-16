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
import org.springframework.web.bind.annotation.RestController;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.event.domain.enums.CategoryStatus;
import vn.eventplatform.event.domain.enums.ReviewStatus;
import vn.eventplatform.event.dto.CategoryDto;
import vn.eventplatform.event.security.SecurityUtils;
import vn.eventplatform.event.service.CategoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category API", description = "Quản lý danh mục sự kiện và quy trình đề xuất danh mục mới")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách danh mục sự kiện")
    public ResponseEntity<ApiResponse<List<CategoryDto.CategoryResponse>>> listCategories(
            @RequestParam(value = "status", required = false) CategoryStatus status,
            Authentication authentication
    ) {
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        List<CategoryDto.CategoryResponse> data = categoryService.listCategories(status, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Xem chi tiết danh mục theo ID hoặc Slug")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> getCategory(
            @PathVariable("idOrSlug") String idOrSlug
    ) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getCategoryByIdOrSlug(idOrSlug)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Tạo mới danh mục sự kiện")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryDto.CreateCategoryRequest request
    ) {
        CategoryDto.CategoryResponse data = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo danh mục sự kiện thành công", data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Cập nhật thông tin danh mục sự kiện")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> updateCategory(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CategoryDto.UpdateCategoryRequest request
    ) {
        CategoryDto.CategoryResponse data = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật danh mục thành công", data));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Bật/tắt trạng thái ACTIVE/INACTIVE cho danh mục")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryResponse>> updateCategoryStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CategoryDto.UpdateCategoryStatusRequest request
    ) {
        CategoryDto.CategoryResponse data = categoryService.updateCategoryStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái danh mục thành công", data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Xóa danh mục sự kiện chưa gắn sự kiện")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable("id") UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa danh mục thành công", null));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer] Gửi đề xuất tạo danh mục sự kiện mới")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryProposalResponse>> createProposal(
            Authentication authentication,
            @Valid @RequestBody CategoryDto.CreateCategoryProposalRequest request
    ) {
        UUID organizerId = SecurityUtils.extractUserId(authentication);
        CategoryDto.CategoryProposalResponse data = categoryService.createProposal(organizerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi đề xuất danh mục mới thành công", data));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "[Organizer/Admin] Xem danh sách yêu cầu đề xuất danh mục")
    public ResponseEntity<ApiResponse<PageResponse<CategoryDto.CategoryProposalResponse>>> listProposals(
            @RequestParam(value = "status", required = false) ReviewStatus status,
            Authentication authentication,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID actorId = SecurityUtils.extractUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        Page<CategoryDto.CategoryProposalResponse> page = categoryService.listProposals(status, actorId, isAdmin, capped);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @PostMapping("/requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Phê duyệt đề xuất và tự động khởi tạo danh mục ACTIVE")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryProposalResponse>> approveProposal(
            @PathVariable("requestId") UUID requestId,
            Authentication authentication
    ) {
        UUID adminId = SecurityUtils.extractUserId(authentication);
        CategoryDto.CategoryProposalResponse data = categoryService.approveProposal(requestId, adminId);
        return ResponseEntity.ok(ApiResponse.ok("Đã phê duyệt đề xuất và tạo danh mục mới", data));
    }

    @PostMapping("/requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] Từ chối đề xuất danh mục mới kèm lý do")
    public ResponseEntity<ApiResponse<CategoryDto.CategoryProposalResponse>> rejectProposal(
            @PathVariable("requestId") UUID requestId,
            Authentication authentication,
            @Valid @RequestBody CategoryDto.RejectCategoryProposalRequest request
    ) {
        UUID adminId = SecurityUtils.extractUserId(authentication);
        CategoryDto.CategoryProposalResponse data = categoryService.rejectProposal(requestId, adminId, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối đề xuất danh mục", data));
    }
}
