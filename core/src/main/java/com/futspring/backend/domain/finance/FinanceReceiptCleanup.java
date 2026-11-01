package com.futspring.backend.domain.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceReceiptCleanup {
    private final FinanceNoticeRepository notices;
    private final FinanceQrRepository qrs;
    private final FinanceFiles files;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Scheduled(cron = "0 */15 * * * *")
    public void cleanup() {
        // Pending status is deliberately irrelevant: access expires independently of review.
        Instant cutoff = clock.instant();
        long after = 0;
        while (true) {
            final long cursor = after;
            Long last = transactions.execute(status -> {
                var batch = notices.expiredAfter(cutoff, cursor, PageRequest.of(0, 100));
                for (var notice : batch) {
                    try {
                        files.delete(notice.getReceiptKey(), false); notice.setReceiptDeletedAt(clock.instant()); notices.save(notice);
                    } catch (IOException e) { log.warn("Expired receipt cleanup failed: notice {}", notice.getId(), e); }
                }
                return batch.isEmpty() ? null : batch.getLast().getId();
            });
            if (last == null) break;
            after = last;
        }
        removeOrphans(false); removeOrphans(true);
    }
    private void removeOrphans(boolean qr) {
        Path dir = files.directory(qr);
        if (!Files.isDirectory(dir)) return;
        try (var paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toInstant().isAfter(clock.instant().minus(Duration.ofHours(1)))) return;
                    String key = path.getFileName().toString();
                    boolean referenced = qr ? qrs.existsByFileKey(key) : notices.existsByReceiptKey(key);
                    if (!referenced) Files.deleteIfExists(path);
                } catch (IOException e) { log.warn("Orphan financial file cleanup failed: {}", path.getFileName(), e); }
            });
        } catch (IOException e) { log.warn("Cannot scan private financial storage", e); }
    }
}
