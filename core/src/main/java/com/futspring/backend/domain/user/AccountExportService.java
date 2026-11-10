package com.futspring.backend.domain.user;

import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.finance.FinanceReadService;
import com.futspring.backend.domain.notification.NotificationPreferenceService;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.PushSubscriptionRepository;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStars;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.pelada.PeladaRef;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.stats.StatsService;
import com.futspring.backend.domain.subscription.PeladaSubscriptionRepository;
import com.futspring.backend.domain.subscription.billing.SubscriptionPaymentRepository;
import com.futspring.backend.domain.subscription.SubscriptionPlans;
import com.futspring.backend.domain.subscription.SubscriptionState;
import com.futspring.backend.domain.tour.UserTourRepository;
import com.futspring.backend.domain.user.dto.AccountExportDTO;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * The caller's data export (LGPD art. 18: access and portability): what each domain keeps about the account, read
 * with one or two queries per block whatever the number of peladas, sessions or messages.
 */
@Service
@RequiredArgsConstructor
public class AccountExportService {

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaRepository peladaRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final PeladaPlayerStarsRepository starsRepository;
    private final StatsService statsService;
    private final DailyAttendanceRepository attendanceRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final MessageRepository messageRepository;
    private final FinanceReadService financeReadService;
    private final PeladaSubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final UserTourRepository tourRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountExportDTO export(String callerEmail) {
        User user = userAuthHelper.getAuthenticatedUser(callerEmail);
        Long userId = user.getId();
        Instant now = clock.instant();

        List<PeladaRef> created = peladaRepository.findCreatedBy(userId);
        return new AccountExportDTO(
                AccountExportDTO.FORMAT_VERSION,
                now,
                new AccountExportDTO.Account(userId, user.getUsername(), user.getEmail(), user.getPosition(),
                        user.getImage(), user.getBackgroundImage(), user.getCreatedAt(), user.getTermsVersion(),
                        user.getTermsAcceptedAt(), user.getTrialUsedAt()),
                memberships(userId, created),
                statsService.getStats(userId, callerEmail),
                statsService.getMatchHistory(userId, callerEmail),
                attendanceRepository.findByUserIdWithDaily(userId).stream()
                        .map(a -> {
                            Daily d = a.getDaily();
                            return new AccountExportDTO.Attendance(d.getId(), d.getPelada().getId(),
                                    d.getPelada().getName(), d.getDailyDate(), d.getStatus(), a.getConfirmedAt());
                        })
                        .toList(),
                bumpRepository.findByUserIdWithDaily(userId).stream()
                        .map(b -> {
                            Daily d = b.getDaily();
                            return new AccountExportDTO.Bump(d.getId(), d.getPelada().getId(), d.getPelada().getName(),
                                    d.getDailyDate(), b.getBumpedAt(), b.getSeenAt());
                        })
                        .toList(),
                messageRepository.findBySenderIdWithPelada(userId).stream()
                        .map(m -> new AccountExportDTO.ChatMessage(m.getPelada().getId(), m.getPelada().getName(),
                                m.getContent(), m.getSentAt().toString()))
                        .toList(),
                financeReadService.accountExport(userId),
                subscriptions(created, now),
                paymentRepository.findByPayer_IdOrderByPaidOnDescIdDesc(userId).stream()
                        .map(p -> new AccountExportDTO.SubscriptionPaymentRow(p.getPeladaName(),
                                SubscriptionPlans.money(p.getAmount()), p.getPaidOn(), p.getPlan(),
                                p.getPeriodStart(), p.getPeriodEnd(), p.getVoidedAt()))
                        .toList(),
                notificationRepository.findByRecipientIdWithPelada(userId).stream()
                        .map(n -> new AccountExportDTO.NotificationRow(n.getType(),
                                n.getPelada() == null ? null : n.getPelada().getName(), n.getCreatedAt(), n.getReadAt()))
                        .toList(),
                notificationPreferenceService.get(callerEmail),
                tourRepository.findByIdUserId(userId).stream()
                        .map(t -> new AccountExportDTO.TourSeen(t.getId().getTour(), t.getCompletedAt()))
                        .toList(),
                pushSubscriptionRepository.findByUser_IdOrderByCreatedAt(userId).stream()
                        .map(s -> new AccountExportDTO.PushDevice(s.getCreatedAt(), s.getLastSuccessAt()))
                        .toList());
    }

    private List<AccountExportDTO.Membership> memberships(Long userId, List<PeladaRef> created) {
        List<PeladaRef> peladas = peladaRepository.findRefsByMemberId(userId);
        if (peladas.isEmpty()) return List.of();
        List<Long> ids = peladas.stream().map(PeladaRef::id).toList();
        Set<Long> createdIds = created.stream().map(PeladaRef::id).collect(Collectors.toSet());
        Set<Long> adminIds = new HashSet<>(peladaRepository.findAdministeredIds(ids, userId));
        Set<Long> mensalistaIds = new HashSet<>(mensalistaRepository.findPeladaIdsByUserAndPeladaIn(userId, ids));
        Map<Long, Integer> stars = starsRepository.findStarsRowsByUser(userId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        return peladas.stream()
                .map(p -> new AccountExportDTO.Membership(p.id(), p.name(), createdIds.contains(p.id()),
                        adminIds.contains(p.id()), mensalistaIds.contains(p.id()),
                        stars.getOrDefault(p.id(), PeladaPlayerStars.DEFAULT_STARS)))
                .toList();
    }

    private List<AccountExportDTO.CreatedPeladaSubscription> subscriptions(List<PeladaRef> created, Instant now) {
        if (created.isEmpty()) return List.of();
        Map<Long, String> names = created.stream().collect(Collectors.toMap(PeladaRef::id, PeladaRef::name));
        return subscriptionRepository.findByPeladaIdIn(names.keySet()).stream()
                .map(s -> {
                    Long peladaId = s.getPelada().getId();
                    return new AccountExportDTO.CreatedPeladaSubscription(peladaId, names.get(peladaId), s.getKind(),
                            SubscriptionState.of(s, now), s.getPlan(), s.getValidUntil(), s.isFounder(),
                            s.getPixClaimedAt(), s.getClaimedPlan());
                })
                .sorted(Comparator.comparing(AccountExportDTO.CreatedPeladaSubscription::peladaId))
                .toList();
    }
}
