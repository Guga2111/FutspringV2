package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private final PeladaSubscriptionRepository repository = mock(PeladaSubscriptionRepository.class);
    private final SubscriptionGuard guard = new SubscriptionGuard(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Pelada pelada = Pelada.builder().id(7L).name("Pelada").build();

    // validUntil relative to now, in seconds (empty = none)
    @ParameterizedTest(name = "{0} valid until now{1}s → writable {2}")
    @CsvSource({
            "TRIAL, 1, true",
            "TRIAL, 0, false",
            "PAID, 1, true",
            "PAID, -604799, true",
            "PAID, -604800, false",
            "PENDING, , false",
            "CANCELED, , false",
            "EXEMPT, , true",
    })
    void followsTheEffectiveState(SubscriptionKind kind, Long offset, boolean writable) {
        Instant validUntil = offset == null ? null : NOW.plusSeconds(offset);
        when(repository.findByPeladaId(7L)).thenReturn(Optional.of(new PeladaSubscription(pelada, kind, validUntil, NOW)));

        assertThat(guard.isWritable(pelada)).isEqualTo(writable);
        if (writable) {
            assertThatCode(() -> guard.requireWritable(pelada)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> guard.requireWritable(pelada))
                    .isInstanceOfSatisfying(AppException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                        assertThat(e.getMessage()).isEqualTo(SubscriptionGuard.READ_ONLY_MESSAGE);
                    });
        }
    }

    @Test
    void peladaWithoutASubscriptionRow_writes() {
        when(repository.findByPeladaId(7L)).thenReturn(Optional.empty());

        assertThat(guard.isWritable(pelada)).isTrue();
        assertThatCode(() -> guard.requireWritable(pelada)).doesNotThrowAnyException();
    }

    // members already in the pelada; validUntil relative to now, in seconds (empty = none); expected status (OK = joins)
    @ParameterizedTest(name = "{0} valid until now{1}s with {2} members → {3}")
    @CsvSource({
            "TRIAL, 1, 29, OK",
            "TRIAL, 1, 30, CONFLICT",
            "PENDING, , 29, OK",
            "PENDING, , 30, CONFLICT",
            "TRIAL, 0, 1, PAYMENT_REQUIRED",
            "PAID, -604800, 1, PAYMENT_REQUIRED",
            "CANCELED, , 1, PAYMENT_REQUIRED",
            "PAID, 1, 30, OK",
            "PAID, -1, 30, OK",
            "EXEMPT, , 30, OK",
    })
    void roomForANewMember(SubscriptionKind kind, Long offset, int members, String expected) {
        Instant validUntil = offset == null ? null : NOW.plusSeconds(offset);
        when(repository.findByPeladaId(7L)).thenReturn(Optional.of(new PeladaSubscription(pelada, kind, validUntil, NOW)));
        Pelada withMembers = peladaWith(members);

        if (expected.equals("OK")) {
            assertThatCode(() -> guard.requireRoomForMember(withMembers)).doesNotThrowAnyException();
        } else {
            HttpStatus status = HttpStatus.valueOf(expected);
            assertThatThrownBy(() -> guard.requireRoomForMember(withMembers))
                    .isInstanceOfSatisfying(AppException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(status);
                        assertThat(e.getMessage()).isEqualTo(status == HttpStatus.CONFLICT
                                ? SubscriptionGuard.TRIAL_CAP_MESSAGE : SubscriptionGuard.READ_ONLY_MESSAGE);
                    });
        }
    }

    @Test
    void peladaWithoutASubscriptionRow_takesMembersWithoutACap() {
        when(repository.findByPeladaId(7L)).thenReturn(Optional.empty());

        assertThatCode(() -> guard.requireRoomForMember(peladaWith(40))).doesNotThrowAnyException();
    }

    private static Pelada peladaWith(int members) {
        Set<User> users = new HashSet<>();
        for (long id = 1; id <= members; id++) users.add(User.builder().id(id).build());
        return Pelada.builder().id(7L).name("Pelada").members(users).build();
    }
}
