package vn.eventplatform.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.user.domain.UserProfile;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmail(String email);

    Optional<UserProfile> findByUsername(String username);

    @Query("SELECT u FROM UserProfile u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<UserProfile> findByIdWithRoles(@Param("id") UUID id);

    @Query("SELECT u FROM UserProfile u WHERE " +
           "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<UserProfile> searchUsers(@Param("keyword") String keyword, Pageable pageable);
}
