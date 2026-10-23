package vn.eventplatform.ticket.repository.projection;

import vn.eventplatform.ticket.domain.CheckInMethod;

public interface MethodCountProjection {
    CheckInMethod getMethod();
    Long getCount();
}
