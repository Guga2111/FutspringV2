package com.futspring.backend.domain.pelada.invitation;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.pelada.Pelada;

public record MemberJoinedEvent(Pelada pelada, User user) { }
