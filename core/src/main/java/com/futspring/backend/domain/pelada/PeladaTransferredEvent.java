package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;

/** The creator ({@code actor}) handed the pelada to {@code newCreator}. */
public record PeladaTransferredEvent(Pelada pelada, User actor, User newCreator) {
}
