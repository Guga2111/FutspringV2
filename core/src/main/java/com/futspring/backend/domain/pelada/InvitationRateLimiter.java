package com.futspring.backend.domain.pelada;

import com.futspring.backend.shared.exception.AppException;
import com.github.benmanes.caffeine.cache.*;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Shared quota for individual creations and link replacements, per actor and pelada (single API instance). */
@Component
public class InvitationRateLimiter {
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder().maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10)).build();
    public void consume(Long userId, Long peladaId) {
        Bucket bucket = buckets.get(userId + ":" + peladaId, key -> Bucket.builder()
                .addLimit(limit -> limit.capacity(10).refillIntervally(10, Duration.ofMinutes(1))).build());
        if (!bucket.tryConsume(1)) throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitos convites. Aguarde um minuto para tentar novamente.");
    }
}
