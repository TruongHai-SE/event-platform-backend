package vn.eventplatform.user.dto;

import lombok.*;
import vn.eventplatform.user.domain.ApplicationStatus;
import vn.eventplatform.user.domain.OrganizationType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerApplicationResponse {

    private UUID id;
    private UUID userId;
    private String username;
    private String userEmail;
    private String organizationName;
    private OrganizationType organizationType;
    private String representativeName;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String websiteUrl;
    private String bio;
    private ApplicationStatus status;
    private UUID reviewedBy;
    private String reviewNote;
    private Instant reviewedAt;
    private Instant createdAt;
}
