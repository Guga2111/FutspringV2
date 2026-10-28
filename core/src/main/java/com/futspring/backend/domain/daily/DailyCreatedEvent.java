package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.user.User;

/** A session was created by an admin, or by the scheduler ({@code actor} null). */
public record DailyCreatedEvent(Daily daily, User actor) {
}
