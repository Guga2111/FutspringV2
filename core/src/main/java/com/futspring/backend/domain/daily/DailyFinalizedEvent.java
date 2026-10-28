package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.user.User;

import java.util.Collection;
import java.util.List;

/**
 * An admin finalized a session. {@code firstFinalize} is false when it was already finished (a second finalize),
 * so the awards are only announced once.
 */
public record DailyFinalizedEvent(Daily daily, User actor, Collection<User> players, boolean firstFinalize,
                                  List<User> puskasWinners, List<User> wiltballWinners,
                                  List<User> artilheiroWinners, List<User> garcomWinners) {
}
