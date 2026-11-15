package com.futspring.backend.domain.pelada.member;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;

import java.util.Collection;

/** These members became mensalistas ({@code granted}) or diaristas; only members whose type changed. */
public record MensalistasChangedEvent(Pelada pelada, User actor, Collection<User> users, boolean granted) {
}
