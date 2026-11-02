package com.futspring.backend.domain.finance.dto;

import com.futspring.backend.domain.finance.FinanceTypes.*;
import java.time.*;
import java.util.List;

public final class FinanceViews {
    private FinanceViews() {}
    public record Permissions(boolean manage, boolean appointManagers, boolean viewCash) {}
    public record Settings(Visibility visibility, String pixKey, String pixRecipient, Long qrId,
                           List<Long> managerIds) {}
    public record Component(String name, String amount) {}
    public record Template(Long id, String name, Classification classification, String description,
                           List<Component> components, String pixKey, String pixRecipient, Long qrId, boolean archived) {}
    public record Totals(String due, String received, String outstanding, long pendingNotices) {}
    public record Collection(Long id, String name, Classification classification, ListStatus status,
                             String description, LocalDate dueDate, Long dailyId, String dailyLabel,
                             String pixKey, String pixRecipient, Long qrId, List<Component> components,
                             Instant publishedAt, Totals totals) {}
    public record Overview(long revision, Permissions permissions, Settings settings, String balance,
                           List<Template> templates, List<Collection> lists, boolean hasMore, long total) {}
    public record Notice(Long id, Long userId, String amount, NoticeStatus status, Instant createdAt,
                         Instant resolvedAt, Long resolvedBy, String reason, Instant receiptExpiresAt,
                         boolean receiptAvailable) {}
    public record Charge(Long id, Long personId, Long userId, String name, List<Component> components,
                         String adjustment, String amountDue, String received, String outstanding,
                         PaymentStatus paymentStatus, boolean overdue, boolean canceled, LocalDate dueDate,
                         List<Notice> notices) {}
    public record Detail(Collection collection, List<Charge> charges) {}
    public record Result(Overview overview, Detail detail) {}
    public record Movement(Long id, Long listId, Long dailyId, String dailyLabel, Long chargeId, Long noticeId,
                           Long originalId, MovementKind kind, String amount, String obligationDelta,
                           LocalDate effectiveDate, PaymentMethod method, String description, String category,
                           Long createdBy, Instant createdAt, boolean reversed) {}
    public record Audit(Long id, Long actorId, String actorName, AuditOperation operation, String target, String reason,
                        Instant createdAt, String beforeData, String afterData) {}
    /** total: every item of the query, for numbered pages. */
    public record Page<T>(List<T> items, boolean hasMore, long total) {}
    public record Candidate(Long userId, String name, boolean mensalista) {}
}
