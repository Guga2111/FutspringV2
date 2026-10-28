package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.AttendanceChangedByAdminEvent;
import com.futspring.backend.domain.daily.DailyCanceledEvent;
import com.futspring.backend.domain.daily.DailyCreatedEvent;
import com.futspring.backend.domain.daily.DailyFinalizedEvent;
import com.futspring.backend.domain.daily.DiaristaBumpedEvent;
import com.futspring.backend.domain.daily.TeamsSortedEvent;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.pelada.AdminsChangedEvent;
import com.futspring.backend.domain.pelada.MembersAddedEvent;
import com.futspring.backend.domain.pelada.MembersRemovedEvent;
import com.futspring.backend.domain.pelada.MensalistasChangedEvent;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaTransferredEvent;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.futspring.backend.domain.notification.NotificationType.*;

/**
 * Turns domain events into notifications. The listeners are synchronous, so they run inside the publisher's
 * transaction: a rollback leaves no notification. The delivery (push, e-mail) is registered for after the commit.
 * The actor is never notified of their own action.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DailyAttendanceRepository attendanceRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final NotificationDelivery delivery;
    private final Clock clock;

    @EventListener
    public void onDiaristaBumped(DiaristaBumpedEvent event) {
        // Saved in this transaction, so it comes from the persistence context
        bumpRepository.findById(event.bumpId()).ifPresent(bump -> create(DIARISTA_BUMPED, List.of(bump.getUser()),
                bump.getBumpedBy(), bump.getDaily()));
    }

    @EventListener
    public void onDailyCanceled(DailyCanceledEvent event) {
        create(SESSION_CANCELED, attendanceRepository.findUsersByDaily(event.daily()), event.actor(), event.daily());
    }

    @EventListener
    public void onAttendanceChangedByAdmin(AttendanceChangedByAdminEvent event) {
        create(event.confirmed() ? ATTENDANCE_CONFIRMED_BY_ADMIN : ATTENDANCE_REMOVED_BY_ADMIN, event.players(),
                event.actor(), event.daily());
    }

    @EventListener
    public void onDailyCreated(DailyCreatedEvent event) {
        create(SESSION_CREATED, event.daily().getPelada().getMembers(), event.actor(), event.daily());
    }

    @EventListener
    public void onTeamsSorted(TeamsSortedEvent event) {
        collapse(TEAMS_SORTED, event.players(), event.actor(), event.daily());
    }

    @EventListener
    public void onDailyFinalized(DailyFinalizedEvent event) {
        collapse(SESSION_FINISHED, event.players(), event.actor(), event.daily());
        if (!event.firstFinalize()) {
            return;
        }
        // System event: the admin who finalized also hears about their own award
        Map<NotificationAward, List<User>> winners = Map.of(
                NotificationAward.PUSKAS, event.puskasWinners(),
                NotificationAward.WILTBALL, event.wiltballWinners(),
                NotificationAward.ARTILHEIRO, event.artilheiroWinners(),
                NotificationAward.GARCOM, event.garcomWinners());
        List<Notification> awards = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (NotificationAward award : NotificationAward.values()) {
            for (User winner : new LinkedHashSet<>(winners.get(award))) {
                Notification n = build(AWARD_WON, winner, null, event.daily().getPelada(), event.daily(), now);
                n.setAward(award);
                awards.add(n);
            }
        }
        save(awards);
    }

    @EventListener
    public void onMembersAdded(MembersAddedEvent event) {
        create(ADDED_TO_PELADA, event.users(), event.actor(), event.pelada());
    }

    @EventListener
    public void onMembersRemoved(MembersRemovedEvent event) {
        create(REMOVED_FROM_PELADA, event.users(), event.actor(), event.pelada());
    }

    @EventListener
    public void onAdminsChanged(AdminsChangedEvent event) {
        create(event.granted() ? ADMIN_GRANTED : ADMIN_REVOKED, event.users(), event.actor(), event.pelada());
    }

    @EventListener
    public void onMensalistasChanged(MensalistasChangedEvent event) {
        create(event.granted() ? MENSALISTA_GRANTED : MENSALISTA_REVOKED, event.users(), event.actor(), event.pelada());
    }

    @EventListener
    public void onPeladaTransferred(PeladaTransferredEvent event) {
        create(PELADA_TRANSFERRED, List.of(event.newCreator()), event.actor(), event.pelada());
    }

    private void create(NotificationType type, Collection<User> recipients, User actor, Daily daily) {
        create(type, recipients, actor, daily.getPelada(), daily);
    }

    private void create(NotificationType type, Collection<User> recipients, User actor, Pelada pelada) {
        create(type, recipients, actor, pelada, null);
    }

    private void create(NotificationType type, Collection<User> recipients, User actor, Pelada pelada, Daily daily) {
        Instant now = Instant.now(clock);
        save(recipientsWithoutActor(recipients, actor).stream()
                .map(recipient -> build(type, recipient, actor, pelada, daily, now))
                .toList());
    }

    /**
     * Repeatable events (re-sorting, re-finalizing): a recipient who still has an unread notification of this type
     * for the session gets it back at the top instead of a second one.
     */
    private void collapse(NotificationType type, Collection<User> recipients, User actor, Daily daily) {
        Set<User> targets = recipientsWithoutActor(recipients, actor);
        if (targets.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        List<Notification> existing = notificationRepository.findUnread(type, daily, targets);
        Set<User> covered = existing.stream().map(Notification::getRecipient).collect(Collectors.toSet());
        existing.forEach(n -> {
            n.setCreatedAt(now);
            n.setActor(actor);
        });
        List<Notification> fresh = targets.stream()
                .filter(t -> !covered.contains(t))
                .map(t -> build(type, t, actor, daily.getPelada(), daily, now))
                .toList();
        List<Notification> all = new ArrayList<>(existing);
        all.addAll(notificationRepository.saveAll(fresh));
        afterCommit(all);
    }

    private static Set<User> recipientsWithoutActor(Collection<User> recipients, User actor) {
        return recipients.stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.equals(actor))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Notification build(NotificationType type, User recipient, User actor, Pelada pelada, Daily daily,
                                      Instant now) {
        return Notification.builder()
                .recipient(recipient)
                .type(type)
                .actor(actor)
                .pelada(pelada)
                .daily(daily)
                .createdAt(now)
                .build();
    }

    private void save(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        afterCommit(notificationRepository.saveAll(notifications));
    }

    // Same pattern as FileUploadService: the delivery only runs once the rows are committed
    private void afterCommit(List<Notification> notifications) {
        if (notifications.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        List<Long> ids = notifications.stream().map(Notification::getId).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delivery.deliver(ids);
            }
        });
    }
}
