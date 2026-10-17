package vn.eventplatform.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import vn.eventplatform.user.domain.ApplicationStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewOrganizerApplicationRequest {

    @NotNull(message = "Trạng thái duyệt đơn không được để trống")
    private ApplicationStatus status;

    @Size(max = 1000, message = "Ghi chú duyệt đơn không được vượt quá 1000 ký tự")
    private String reviewNote;
}
