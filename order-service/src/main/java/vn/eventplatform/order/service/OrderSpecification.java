package vn.eventplatform.order.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> filterOrders(
            UUID userId,
            OrderStatus status,
            String orderCode,
            UUID eventId,
            UUID showTimeId,
            String keyword,
            Instant fromDate,
            Instant toDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(orderCode)) {
                String pattern = "%" + orderCode.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("orderCode")), pattern));
            }

            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }

            if (showTimeId != null) {
                predicates.add(cb.equal(root.get("showTimeId"), showTimeId));
            }

            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("orderCode")), pattern);
                Predicate nameMatch = cb.like(cb.lower(cb.coalesce(root.get("customerName"), "")), pattern);
                Predicate emailMatch = cb.like(cb.lower(cb.coalesce(root.get("customerEmail"), "")), pattern);
                Predicate phoneMatch = cb.like(cb.lower(cb.coalesce(root.get("customerPhone"), "")), pattern);
                predicates.add(cb.or(codeMatch, nameMatch, emailMatch, phoneMatch));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
