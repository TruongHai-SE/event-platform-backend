package vn.eventplatform.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.eventplatform.event.domain.Category;
import vn.eventplatform.event.domain.CategoryRequest;
import vn.eventplatform.event.domain.Event;
import vn.eventplatform.event.domain.Seat;
import vn.eventplatform.event.domain.SeatSection;
import vn.eventplatform.event.domain.ShowTime;
import vn.eventplatform.event.domain.ShowTimeTicketInventory;
import vn.eventplatform.event.domain.TicketType;
import vn.eventplatform.event.dto.CategoryDto;
import vn.eventplatform.event.dto.EventDto;

@Mapper(componentModel = "spring")
public interface EventMapper {

    CategoryDto.CategoryResponse toCategoryResponse(Category category);

    @Mapping(target = "organizerId", source = "requesterId")
    @Mapping(target = "requestedName", source = "proposedName")
    @Mapping(target = "normalizedName", source = "normalizedProposedName")
    @Mapping(target = "rejectionReason", source = "reviewNote")
    @Mapping(target = "createdCategoryId", source = "categoryId")
    CategoryDto.CategoryProposalResponse toCategoryProposalResponse(CategoryRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "city", source = "venueAddress")
    @Mapping(target = "minPrice", ignore = true)
    @Mapping(target = "nextShowTime", ignore = true)
    EventDto.EventSummaryResponse toEventSummaryResponse(Event event);

    EventDto.TicketTypeResponse toTicketTypeResponse(TicketType ticketType);

    @Mapping(target = "showTimeId", source = "showTime.id")
    @Mapping(target = "ticketTypeId", source = "ticketType.id")
    @Mapping(target = "ticketTypeName", source = "ticketType.name")
    @Mapping(target = "price", source = "ticketType.price")
    @Mapping(target = "availableQuantity", expression = "java(inventory.getAvailableQuantity())")
    EventDto.TicketInventoryResponse toTicketInventoryResponse(ShowTimeTicketInventory inventory);

    @Mapping(target = "venueAddress", ignore = true)
    @Mapping(target = "capacity", ignore = true)
    @Mapping(target = "inventories", ignore = true)
    EventDto.ShowTimeResponse toShowTimeResponse(ShowTime showTime);

    @Mapping(target = "sectionId", source = "section.id")
    @Mapping(target = "assignedTicketTypeId", source = "ticketType.id")
    @Mapping(target = "availabilityStatus", ignore = true)
    EventDto.SeatResponse toSeatResponse(Seat seat);

    @Mapping(target = "assignedTicketTypeId", ignore = true)
    @Mapping(target = "seats", ignore = true)
    EventDto.SeatSectionResponse toSeatSectionResponse(SeatSection section);
}
