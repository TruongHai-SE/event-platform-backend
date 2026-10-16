package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.event.domain.Category;
import vn.eventplatform.event.domain.enums.CategoryStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByStatusOrderBySortOrderAscNameAsc(CategoryStatus status);

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsBySlug(String slug);

    boolean existsByNormalizedNameAndStatusIn(String normalizedName, Collection<CategoryStatus> statuses);

    boolean existsByNormalizedNameAndStatusInAndIdNot(String normalizedName, Collection<CategoryStatus> statuses, UUID id);
}
