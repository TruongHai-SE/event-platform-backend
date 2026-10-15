package vn.eventplatform.promotion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.promotion.domain.Promotion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository
        extends JpaRepository<Promotion, UUID> {

    Optional<Promotion> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update Promotion p set p.usedCount = p.usedCount + 1 where p.id = :id and p.isActive = true and (p.totalUsageLimit is null or p.usedCount < p.totalUsageLimit)")
    int reserveUsageSlot(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("update Promotion p set p.usedCount = p.usedCount - 1 where p.id = :id and p.usedCount > 0")
    int releaseUsageSlot(@Param("id") UUID id);

    List<Promotion> findByEventId(UUID eventId);

    List<Promotion> findByOrganizerId(UUID organizerId);

    List<Promotion> findByIsActive(Boolean isActive);

    List<Promotion> findByEventIdAndIsActive(
            UUID eventId,
            Boolean isActive
    );
}
