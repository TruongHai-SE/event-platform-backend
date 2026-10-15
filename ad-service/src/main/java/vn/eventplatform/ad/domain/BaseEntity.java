package vn.eventplatform.ad.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Getter @Setter @MappedSuperclass public abstract class BaseEntity { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="created_at") private Instant createdAt; @Column(name="updated_at") private Instant updatedAt; @PrePersist void create(){createdAt=updatedAt=Instant.now();} @PreUpdate void update(){updatedAt=Instant.now();} }
