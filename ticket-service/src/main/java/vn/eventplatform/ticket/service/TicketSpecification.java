package vn.eventplatform.ticket.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.dto.TicketFilterRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TicketSpecification {

    private TicketSpecification() {
    }

    public static Specification<Ticket> filterCustomerTickets(UUID userId, TicketFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }

            if (filter != null) {
                applyCommonFilters(root, cb, predicates, filter);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Ticket> filterAdminTickets(TicketFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                applyCommonFilters(root, cb, predicates, filter);

                if (filter.getTicketTypeId() != null) {
                    predicates.add(cb.equal(root.get("ticketTypeId"), filter.getTicketTypeId()));
                }
                if (filter.getUserId() != null) {
                    predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
                }
                if (filter.getOrderId() != null) {
                    predicates.add(cb.equal(root.get("orderId"), filter.getOrderId()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void applyCommonFilters(Root<Ticket> root, CriteriaBuilder cb, List<Predicate> predicates, TicketFilterRequest filter) {
        if (filter.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getEventId() != null) {
            predicates.add(cb.equal(root.get("eventId"), filter.getEventId()));
        }
        if (filter.getShowTimeId() != null) {
            predicates.add(cb.equal(root.get("showTimeId"), filter.getShowTimeId()));
        }

        if (filter.getKeyword() != null && !filter.getKeyword().trim().isEmpty()) {
            String kw = filter.getKeyword().trim();
            String searchPattern = "%" + kw.toLowerCase() + "%";
            List<Predicate> searchPredicates = new ArrayList<>();

            searchPredicates.add(cb.like(cb.lower(root.get("ticketTypeName")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("seatNumber")), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("id").as(String.class)), searchPattern));
            searchPredicates.add(cb.like(cb.lower(root.get("orderId").as(String.class)), searchPattern));

            try {
                UUID searchUuid = UUID.fromString(kw);
                searchPredicates.add(cb.equal(root.get("id"), searchUuid));
                searchPredicates.add(cb.equal(root.get("orderId"), searchUuid));
                searchPredicates.add(cb.equal(root.get("userId"), searchUuid));
                searchPredicates.add(cb.equal(root.get("eventId"), searchUuid));
                searchPredicates.add(cb.equal(root.get("showTimeId"), searchUuid));
            } catch (IllegalArgumentException ignored) {
                // Nếu không phải UUID hợp lệ, tìm thêm trong qrPayload
                searchPredicates.add(cb.like(cb.lower(root.get("qrPayload")), searchPattern));
            }

            predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
        }
    }
}
