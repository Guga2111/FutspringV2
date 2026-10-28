package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** An admin sorted the session's teams; {@code players} are the players on them. */
public record TeamsSortedEvent(Daily daily, User actor, Collection<User> players) {
}
