package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.user.User;

/** An admin canceled a session; published inside the status change's transaction. */
public record DailyCanceledEvent(Daily daily, User actor) {
}
