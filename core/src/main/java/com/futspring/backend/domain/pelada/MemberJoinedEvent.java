package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;

public record MemberJoinedEvent(Pelada pelada, User user) { }
