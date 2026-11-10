package com.futspring.backend.domain.platform;

import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRef;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.platform.dto.PlatformViews;
import com.futspring.backend.domain.subscription.PeladaSubscription;
import com.futspring.backend.domain.subscription.PeladaSubscriptionRepository;
import com.futspring.backend.domain.subscription.billing.SubscriptionEvent;
import com.futspring.backend.domain.subscription.billing.SubscriptionEventRepository;
import com.futspring.backend.domain.subscription.billing.SubscriptionPayment;
import com.futspring.backend.domain.subscription.billing.SubscriptionPaymentRepository;
import com.futspring.backend.domain.subscription.SubscriptionPlan;
import com.futspring.backend.domain.subscription.SubscriptionPlans;
import com.futspring.backend.domain.subscription.SubscriptionService;
import com.futspring.backend.domain.subscription.SubscriptionState;
import com.futspring.backend.domain.subscription.SubscriptionStatus;
import com.futspring.backend.domain.subscription.SubscriptionStatusFilter;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import com.futspring.backend.shared.exception.AppException;
import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What the platform admin reads. Every list reads a constant number of queries whatever its size. */
@Service
@RequiredArgsConstructor
public class PlatformReadService {

    static final int MAX_PAGE_SIZE = 50;
    private static final int ATTENTION_LIMIT = 20;
    private static final int HISTORY_LIMIT = 50;
    private static final Duration TRIAL_ENDING_WINDOW = Duration.ofDays(7);

    private final PlatformAccess access;
    private final PeladaSubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionEventRepository eventRepository;
    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PlatformViews.Overview overview(String email) {
        access.requirePlatformAdmin(email);
        Instant now = Instant.now(clock);

        Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            counts.put(status, subscriptionRepository.count(SubscriptionStatusFilter.of(status, now)));
        }
        PageRequest attention = PageRequest.of(0, ATTENTION_LIMIT);
        List<PlatformViews.Attention> trialsEndingSoon = subscriptionRepository
                .findTrialsEndingBetween(now, now.plus(TRIAL_ENDING_WINDOW), attention).stream()
                .map(s -> new PlatformViews.Attention(s.getPelada().getId(), s.getPelada().getName(), s.getValidUntil()))
                .toList();
        List<PlatformViews.Attention> pixClaimed = subscriptionRepository.findPixClaimed(attention).stream()
                .map(s -> new PlatformViews.Attention(s.getPelada().getId(), s.getPelada().getName(), s.getPixClaimedAt()))
                .toList();

        YearMonth thisMonth = YearMonth.from(LocalDate.ofInstant(now, SubscriptionService.BILLING_ZONE));
        YearMonth first = thisMonth.minusMonths(11);
        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        for (Object[] row : paymentRepository.sumByMonthSince(first.atDay(1))) {
            byMonth.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()), (BigDecimal) row[2]);
        }
        List<PlatformViews.MonthRevenue> revenueByMonth = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(thisMonth); month = month.plusMonths(1)) {
            revenueByMonth.add(new PlatformViews.MonthRevenue(month.toString(),
                    SubscriptionPlans.money(byMonth.getOrDefault(month, BigDecimal.ZERO))));
        }

        BigDecimal recurring = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.sumCoveringByPlan(now)) {
            BigDecimal total = (BigDecimal) row[1];
            recurring = recurring.add(row[0] == SubscriptionPlan.ANNUAL
                    ? total.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP) : total);
        }

        PlatformViews.Users users = new PlatformViews.Users(userRepository.countByDeletedAtIsNull(),
                userRepository.countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(30))));
        return new PlatformViews.Overview(counts, trialsEndingSoon, pixClaimed,
                revenueByMonth.getLast().total(), revenueByMonth, SubscriptionPlans.money(recurring), users);
    }

    /** {@code state} filters by the effective state (in SQL); {@code q} matches the pelada, creator name or e-mail. */
    @Transactional(readOnly = true)
    public PlatformViews.Page<PlatformViews.PeladaItem> peladas(String email, SubscriptionStatus state, String q,
                                                                int page, int size) {
        access.requirePlatformAdmin(email);
        Instant now = Instant.now(clock);
        Specification<PeladaSubscription> spec = (root, query, cb) -> cb.conjunction();
        if (state != null) spec = spec.and(SubscriptionStatusFilter.of(state, now));
        String term = search(q);
        if (!term.isEmpty()) {
            String like = "%" + term + "%";
            spec = spec.and((root, query, cb) -> {
                Join<PeladaSubscription, Pelada> pelada = root.join("pelada");
                Join<Pelada, User> creator = pelada.join("creator");
                return cb.or(cb.like(cb.lower(pelada.get("name")), like),
                        cb.like(cb.lower(creator.get("username")), like),
                        cb.like(cb.lower(creator.get("email")), like));
            });
        }
        var rows = subscriptionRepository.findAll(spec, PageRequest.of(page(page), size(size),
                Sort.by("pelada.name", "id")));
        List<Long> ids = rows.stream().map(s -> s.getPelada().getId()).toList();
        Map<Long, Integer> members = memberCounts(ids);
        Map<Long, LocalDate> lastSessions = lastSessionDates(ids);
        List<PlatformViews.PeladaItem> items = rows.stream().map(s -> {
            Pelada pelada = s.getPelada();
            return new PlatformViews.PeladaItem(pelada.getId(), pelada.getName(), PublicUserDTO.from(pelada.getCreator()),
                    members.getOrDefault(pelada.getId(), 0), SubscriptionState.of(s, now), s.getValidUntil(),
                    s.isFounder(), s.getPixClaimedAt(), lastSessions.get(pelada.getId()));
        }).toList();
        return new PlatformViews.Page<>(items, rows.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PlatformViews.PeladaDetail pelada(String email, Long peladaId) {
        access.requirePlatformAdmin(email);
        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        return detailOf(pelada);
    }

    /** The detail of a pelada already loaded (and checked) by the caller; runs in the caller's transaction. */
    public PlatformViews.PeladaDetail detailOf(Pelada pelada) {
        Instant now = Instant.now(clock);
        Long id = pelada.getId();
        int memberCount = memberCounts(List.of(id)).getOrDefault(id, 0);
        PlatformViews.PeladaInfo info = new PlatformViews.PeladaInfo(id, pelada.getName(), pelada.getImage(),
                PublicUserDTO.from(pelada.getCreator()), pelada.getCreator().getEmail(), memberCount,
                lastSessionDates(List.of(id)).get(id));
        PlatformViews.Subscription subscription = subscriptionRepository.findByPeladaId(id)
                .map(s -> subscriptionView(s, memberCount, now)).orElse(null);
        PageRequest latest = PageRequest.of(0, HISTORY_LIMIT);
        List<PlatformViews.Payment> payments = paymentRepository.findByPeladaIdOrderByPaidOnDescIdDesc(id, latest)
                .stream().map(PlatformReadService::paymentView).toList();
        List<PlatformViews.Event> events = eventRepository.findByPeladaIdOrderByCreatedAtDescIdDesc(id, latest)
                .stream().map(PlatformReadService::eventView).toList();
        return new PlatformViews.PeladaDetail(info, subscription, payments, events);
    }

    /** {@code from} / {@code to} are inclusive on paidOn; without them every payment. */
    @Transactional(readOnly = true)
    public PlatformViews.Payments payments(String email, LocalDate from, LocalDate to, int page, int size) {
        access.requirePlatformAdmin(email);
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        LocalDate start = from != null ? from : LocalDate.EPOCH;
        // paidOn is never in the future
        LocalDate end = to != null ? to : LocalDate.ofInstant(Instant.now(clock), SubscriptionService.BILLING_ZONE).plusDays(1);
        var rows = paymentRepository.findByPaidOnBetweenOrderByPaidOnDescIdDesc(start, end,
                PageRequest.of(page(page), size(size)));
        return new PlatformViews.Payments(rows.stream().map(PlatformReadService::paymentView).toList(),
                rows.getTotalElements(), SubscriptionPlans.money(paymentRepository.sumBetween(start, end)));
    }

    /** Every account (deleted ones included), newest first; {@code q} matches name or e-mail. */
    @Transactional(readOnly = true)
    public PlatformViews.Page<PlatformViews.UserItem> users(String email, String q, int page, int size) {
        access.requirePlatformAdmin(email);
        var rows = userRepository.searchForPlatform(search(q), PageRequest.of(page(page), size(size)));
        List<Long> ids = rows.stream().map(User::getId).toList();
        Map<Long, List<PeladaRef>> created = new HashMap<>();
        Map<Long, Long> memberships = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : peladaRepository.findCreatedByAny(ids)) {
                created.computeIfAbsent((Long) row[0], k -> new ArrayList<>())
                        .add(new PeladaRef((Long) row[1], (String) row[2]));
            }
            for (Object[] row : peladaRepository.countMembershipsByUsers(ids)) {
                memberships.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        List<PlatformViews.UserItem> items = rows.stream().map(u -> new PlatformViews.UserItem(u.getId(),
                u.getUsername(), u.getEmail(), u.getCreatedAt(), created.getOrDefault(u.getId(), List.of()),
                memberships.getOrDefault(u.getId(), 0L), u.getTrialUsedAt(), u.isDeleted())).toList();
        return new PlatformViews.Page<>(items, rows.getTotalElements());
    }

    static PlatformViews.Payment paymentView(SubscriptionPayment p) {
        return new PlatformViews.Payment(p.getId(), p.getPeladaId(), p.getPeladaName(),
                p.getPayer() == null ? null : p.getPayer().getId(), p.getPayerName(), SubscriptionPlans.money(p.getAmount()),
                p.getPaidOn(), p.getPlan(), p.getPeriodStart(), p.getPeriodEnd(), PublicUserDTO.from(p.getRecordedBy()),
                p.getNote(), p.getVoidedAt(), p.getVoidReason(), p.getCreatedAt());
    }

    private static PlatformViews.Event eventView(SubscriptionEvent e) {
        return new PlatformViews.Event(e.getId(), e.getType(), e.getActor() == null ? null : PublicUserDTO.from(e.getActor()),
                e.getKindBefore(), e.getKindAfter(), e.getValidUntilBefore(), e.getValidUntilAfter(), e.getNote(),
                e.getCreatedAt());
    }

    private static PlatformViews.Subscription subscriptionView(PeladaSubscription s, int memberCount, Instant now) {
        SubscriptionStatus status = SubscriptionState.of(s, now);
        List<PlatformViews.Price> prices = Arrays.stream(SubscriptionPlan.values())
                .map(plan -> new PlatformViews.Price(plan, SubscriptionPlans.money(SubscriptionPlans.price(plan, memberCount))))
                .toList();
        return new PlatformViews.Subscription(s.getKind(), status, status.writable(), s.getValidUntil(), s.getPlan(),
                s.isFounder(), s.getPixClaimedAt(), s.getClaimedPlan(), prices, s.getCreatedAt(), s.getUpdatedAt());
    }

    private Map<Long, Integer> memberCounts(Collection<Long> peladaIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (peladaIds.isEmpty()) return counts;
        for (Object[] row : peladaRepository.countMembersByIds(peladaIds)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private Map<Long, LocalDate> lastSessionDates(Collection<Long> peladaIds) {
        Map<Long, LocalDate> dates = new HashMap<>();
        if (peladaIds.isEmpty()) return dates;
        for (Object[] row : dailyRepository.findLastFinishedDates(peladaIds)) {
            dates.put((Long) row[0], (LocalDate) row[1]);
        }
        return dates;
    }

    private static String search(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    private static int page(int page) {
        return Math.max(page, 0);
    }

    private static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
