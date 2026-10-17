package vn.eventplatform.user.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.user.domain.ApplicationStatus;
import vn.eventplatform.user.domain.OrganizerApplication;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizerApplicationRepository extends JpaRepository<OrganizerApplication, UUID> {

    boolean existsByUserProfile_IdAndStatus(UUID userId, ApplicationStatus status);

    @Query("SELECT a FROM OrganizerApplication a JOIN FETCH a.userProfile WHERE a.userProfile.id = :userId ORDER BY a.createdAt DESC")
    java.util.List<OrganizerApplication> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM OrganizerApplication a JOIN FETCH a.userProfile u LEFT JOIN FETCH u.roles WHERE a.id = :id")
    Optional<OrganizerApplication> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = "SELECT a FROM OrganizerApplication a JOIN FETCH a.userProfile WHERE (:status IS NULL OR a.status = :status)",
            countQuery = "SELECT COUNT(a) FROM OrganizerApplication a WHERE (:status IS NULL OR a.status = :status)"
    )
    Page<OrganizerApplication> findByStatusFilter(@Param("status") ApplicationStatus status, Pageable pageable);
}
