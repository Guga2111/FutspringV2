package com.futspring.backend.domain.subscription.checkout;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;

/** The creator said "Já fiz o Pix" for the first time (a plan change or a repeat doesn't publish it again). */
public record PixClaimedEvent(Pelada pelada, User actor) {
}
