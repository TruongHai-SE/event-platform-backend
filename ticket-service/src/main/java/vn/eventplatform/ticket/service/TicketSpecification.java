package vn.eventplatform.ticket.service;

import jakarta.persistence.criteria.Predicate;
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
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                if (filter.getEventId() != null) {
                    predicates.add(cb.equal(root.get("eventId"), filter.getEventId()));
                }
                if (filter.getShowTimeId() != null) {
                    predicates.add(cb.equal(root.get("showTimeId"), filter.getShowTimeId()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
