package vn.eventplatform.event.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import vn.eventplatform.event.domain.Event;
import vn.eventplatform.event.domain.ShowTime;
import vn.eventplatform.event.domain.TicketType;
import vn.eventplatform.event.domain.enums.CategoryStatus;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.domain.enums.ShowTimeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EventSpecification {

    private EventSpecification() {
    }

    public static Specification<Event> filterPublicEvents(
            String keyword,
            UUID categoryId,
            String categorySlug,
            String city,
            Instant startFrom,
            Instant endTo,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));
            predicates.add(cb.equal(root.get("category").get("status"), CategoryStatus.PUBLISHED));

            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate venueMatch = cb.like(cb.lower(cb.coalesce(root.get("venueName"), "")), pattern);
                Predicate addressMatch = cb.like(cb.lower(cb.coalesce(root.get("venueAddress"), "")), pattern);
                predicates.add(cb.or(titleMatch, venueMatch, addressMatch));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (StringUtils.hasText(categorySlug)) {
                predicates.add(cb.equal(
                        cb.lower(root.get("category").get("slug")),
                        categorySlug.trim().toLowerCase(Locale.ROOT)
                ));
            }

            if (StringUtils.hasText(city)) {
                String cityPattern = "%" + city.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("venueAddress"), "")), cityPattern),
                        cb.like(cb.lower(cb.coalesce(root.get("venueName"), "")), cityPattern)
                ));
            }

            if (startFrom != null || endTo != null) {
                Subquery<UUID> showTimeSubquery = query.subquery(UUID.class);
                Root<ShowTime> stRoot = showTimeSubquery.from(ShowTime.class);
                List<Predicate> stPredicates = new ArrayList<>();
                stPredicates.add(cb.equal(stRoot.get("event"), root));
                stPredicates.add(cb.equal(stRoot.get("status"), ShowTimeStatus.SCHEDULED));
                if (startFrom != null) {
                    stPredicates.add(cb.greaterThanOrEqualTo(stRoot.get("startTime"), startFrom));
                }
                if (endTo != null) {
                    stPredicates.add(cb.lessThanOrEqualTo(stRoot.get("startTime"), endTo));
                }
                showTimeSubquery.select(stRoot.get("id")).where(stPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(showTimeSubquery));
            }

            if (minPrice != null || maxPrice != null) {
                Subquery<UUID> ticketSubquery = query.subquery(UUID.class);
                Root<TicketType> ttRoot = ticketSubquery.from(TicketType.class);
                List<Predicate> ttPredicates = new ArrayList<>();
                ttPredicates.add(cb.equal(ttRoot.get("event"), root));
                if (minPrice != null) {
                    ttPredicates.add(cb.greaterThanOrEqualTo(ttRoot.get("price"), minPrice));
                }
                if (maxPrice != null) {
                    ttPredicates.add(cb.lessThanOrEqualTo(ttRoot.get("price"), maxPrice));
                }
                ticketSubquery.select(ttRoot.get("id")).where(ttPredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(ticketSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Event> filterManagedEvents(
            UUID organizerId,
            EventStatus status,
            UUID categoryId,
            String keyword
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizerId != null) {
                predicates.add(cb.equal(root.get("organizerId"), organizerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
