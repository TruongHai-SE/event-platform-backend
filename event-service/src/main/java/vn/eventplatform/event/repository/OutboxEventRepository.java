package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.event.domain.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtDesc(UUID aggregateId);
}
