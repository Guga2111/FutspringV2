package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.finance.dto.FinanceAccountExport;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.notification.dto.NotificationPreferenceDTO;
import com.futspring.backend.domain.stats.dto.StatsDTO;
import com.futspring.backend.domain.stats.dto.UserMatchHistoryDTO;
import com.futspring.backend.domain.subscription.SubscriptionKind;
import com.futspring.backend.domain.subscription.SubscriptionPlan;
import com.futspring.backend.domain.subscription.SubscriptionStatus;
import com.futspring.backend.domain.tour.TourKey;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the platform keeps about the caller (LGPD art. 18: access and portability), as served by
 * GET /users/me/export. Only the caller's own data: no other member's e-mail, charges or messages.
 */
public record AccountExportDTO(
        int formatVersion,
        Instant exportedAt,
        Account account,
        List<Membership> peladas,
        StatsDTO stats,
        UserMatchHistoryDTO matchHistory,
        List<Attendance> attendance,
        List<Bump> bumps,
        List<ChatMessage> messages,
        FinanceAccountExport finance,
        List<CreatedPeladaSubscription> subscriptions,
        List<SubscriptionPaymentRow> subscriptionPayments,
        List<NotificationRow> notifications,
        List<NotificationPreferenceDTO> notificationPreferences,
        List<TourSeen> toursSeen,
        List<PushDevice> pushDevices) {

    public static final int FORMAT_VERSION = 1;

    public record Account(
            Long id,
            String username,
            String email,
            String position,
            String image,
            String backgroundImage,
            Instant createdAt,
            String termsVersion,
            Instant termsAcceptedAt,
            Instant trialUsedAt) {
    }

    public record Membership(Long peladaId, String peladaName, boolean isCreator, boolean isAdmin,
                             boolean isMensalista, int stars) {
    }

    public record Attendance(Long dailyId, Long peladaId, String peladaName, LocalDate date, DailyStatus status,
                             Instant confirmedAt) {
    }

    public record Bump(Long dailyId, Long peladaId, String peladaName, LocalDate date, Instant bumpedAt,
                       Instant seenAt) {
    }

    public record ChatMessage(Long peladaId, String peladaName, String content, String sentAt) {
    }

    public record CreatedPeladaSubscription(Long peladaId, String peladaName, SubscriptionKind kind,
                                            SubscriptionStatus status, SubscriptionPlan plan, Instant validUntil,
                                            boolean founder, Instant pixClaimedAt, SubscriptionPlan claimedPlan) {
    }

    public record SubscriptionPaymentRow(String peladaName, String amount, LocalDate paidOn,
                                         SubscriptionPlan plan, Instant periodStart, Instant periodEnd,
                                         Instant voidedAt) {
    }

    public record NotificationRow(NotificationType type, String peladaName, Instant createdAt, Instant readAt) {
    }

    public record TourSeen(TourKey tour, Instant completedAt) {
    }

    public record PushDevice(Instant createdAt, Instant lastSuccessAt) {
    }
}
