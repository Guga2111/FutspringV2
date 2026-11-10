package com.futspring.backend.domain.daily.attendance;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** An admin confirmed ({@code confirmed}) or removed the attendance of these players. */
public record AttendanceChangedByAdminEvent(Daily daily, User actor, Collection<User> players, boolean confirmed) {
}
