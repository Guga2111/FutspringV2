package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** An admin added these users to the pelada. */
public record MembersAddedEvent(Pelada pelada, User actor, Collection<User> users) {
}
