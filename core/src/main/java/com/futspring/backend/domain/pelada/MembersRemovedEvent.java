package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** An admin removed these members from the pelada. */
public record MembersRemovedEvent(Pelada pelada, User actor, Collection<User> users) {
}
