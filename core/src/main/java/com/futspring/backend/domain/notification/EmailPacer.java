package com.futspring.backend.domain.notification;

import io.github.bucket4j.BlockingStrategy;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keeps notification e-mails under Resend's ~2 requests per second: acquire() blocks the (single) e-mail thread
 * until the next send may go. One token every 500 ms and no burst, so no 1-second window ever holds more than two
 * sends (a 2-token bucket would let three through: two at once, the third half a second later). 30 e-mails take
 * about 15 s, in the background.
 */
@Component
public class EmailPacer {

    static final int PER_SECOND = 2;
    static final Duration INTERVAL = Duration.ofSeconds(1).dividedBy(PER_SECOND);

    private final Bucket bucket;
    private final BlockingStrategy blockingStrategy;

    public EmailPacer() {
        this(TimeMeter.SYSTEM_NANOTIME, BlockingStrategy.PARKING);
    }

    // Tests pass a fake clock and a strategy that advances it instead of sleeping
    EmailPacer(TimeMeter timeMeter, BlockingStrategy blockingStrategy) {
        this.bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(1).refillGreedy(1, INTERVAL))
                .withCustomTimePrecision(timeMeter)
                .build();
        this.blockingStrategy = blockingStrategy;
    }

    public void acquire() throws InterruptedException {
        bucket.asBlocking().consume(1, blockingStrategy);
    }
}
