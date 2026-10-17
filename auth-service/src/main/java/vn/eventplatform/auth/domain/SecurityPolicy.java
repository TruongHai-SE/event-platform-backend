package vn.eventplatform.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPolicy {

    @Id
    @Builder.Default
    private Short id = 1;

    @Column(name = "max_failed_attempts", nullable = false)
    @Builder.Default
    private int maxFailedAttempts = 5;

    @Column(name = "lockout_duration_minutes", nullable = false)
    @Builder.Default
    private int lockoutDurationMinutes = 15;

    @Column(name = "otp_expiration_minutes", nullable = false)
    @Builder.Default
    private int otpExpirationMinutes = 5;

    @Column(name = "max_otp_attempts", nullable = false)
    @Builder.Default
    private int maxOtpAttempts = 5;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
