package com.futspring.backend.domain.pelada.member;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** These members became admins ({@code granted}) or stopped being admins; only members whose status changed. */
public record AdminsChangedEvent(Pelada pelada, User actor, Collection<User> users, boolean granted) {
}
