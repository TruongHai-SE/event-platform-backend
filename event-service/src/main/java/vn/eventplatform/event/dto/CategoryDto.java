package vn.eventplatform.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.event.domain.enums.CategoryStatus;
import vn.eventplatform.event.domain.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public final class CategoryDto {

    private CategoryDto() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCategoryRequest {
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 120, message = "Tên danh mục tối đa 120 ký tự")
        private String name;

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        private String description;

        private CategoryStatus status;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCategoryRequest {
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 120, message = "Tên danh mục tối đa 120 ký tự")
        private String name;

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCategoryStatusRequest {
        @NotNull(message = "Trạng thái danh mục không được để trống")
        private CategoryStatus status;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCategoryProposalRequest {
        @NotBlank(message = "Tên danh mục đề xuất không được để trống")
        @Size(max = 120, message = "Tên danh mục đề xuất tối đa 120 ký tự")
        private String requestedName;

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectCategoryProposalRequest {
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 1000, message = "Lý do từ chối tối đa 1000 ký tự")
        private String rejectionReason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResponse {
        private UUID id;
        private String name;
        private String slug;
        private String description;
        private CategoryStatus status;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryProposalResponse {
        private UUID id;
        private UUID organizerId;
        private String requestedName;
        private String normalizedName;
        private String description;
        private ReviewStatus status;
        private String rejectionReason;
        private UUID reviewedBy;
        private Instant reviewedAt;
        private UUID createdCategoryId;
        private Instant createdAt;
    }
}
