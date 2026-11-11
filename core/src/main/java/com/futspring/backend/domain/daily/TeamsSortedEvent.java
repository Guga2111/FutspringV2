package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** An admin sorted or built the session's teams by hand; {@code players} are the players on them. */
public record TeamsSortedEvent(Daily daily, User actor, Collection<User> players) {
}
