package com.futspring.backend.domain.notification;

import io.github.bucket4j.BlockingStrategy;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPacerTest {

    // A clock that only moves when the pacer parks, so the test measures the pacing without sleeping
    private static final class FakeTime implements TimeMeter, BlockingStrategy {
        long nanos;
        final List<Long> parks = new ArrayList<>();

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        @Override
        public void park(long nanosToPark) {
            parks.add(nanosToPark);
            nanos += nanosToPark;
        }
    }

    @Test
    void neverMoreThanTwoPerSecond() throws InterruptedException {
        FakeTime time = new FakeTime();
        EmailPacer pacer = new EmailPacer(time, time);
        List<Long> sentAt = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            pacer.acquire();
            sentAt.add(time.nanos);
        }

        // the first right away, then one every half second: 6 e-mails in 2.5 s
        assertThat(time.nanos).isEqualTo(Duration.ofMillis(2500).toNanos());
        for (int i = 1; i < sentAt.size(); i++) {
            assertThat(sentAt.get(i) - sentAt.get(i - 1)).isGreaterThanOrEqualTo(EmailPacer.INTERVAL.toNanos());
        }
        // any 1-second window holds at most two sends
        for (int i = EmailPacer.PER_SECOND; i < sentAt.size(); i++) {
            assertThat(sentAt.get(i) - sentAt.get(i - EmailPacer.PER_SECOND))
                    .isGreaterThanOrEqualTo(Duration.ofSeconds(1).toNanos());
        }
    }

    @Test
    void afterIdleTime_theNextSendGoesAtOnce_withoutBuildingABurst() throws InterruptedException {
        FakeTime time = new FakeTime();
        EmailPacer pacer = new EmailPacer(time, time);
        pacer.acquire();

        time.nanos += Duration.ofSeconds(5).toNanos();
        pacer.acquire();
        assertThat(time.parks).isEmpty();

        pacer.acquire();
        assertThat(time.parks).containsExactly(EmailPacer.INTERVAL.toNanos());
    }
}
