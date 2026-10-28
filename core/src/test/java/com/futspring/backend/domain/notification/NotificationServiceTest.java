package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.AttendanceChangedByAdminEvent;
import com.futspring.backend.domain.daily.DailyCanceledEvent;
import com.futspring.backend.domain.daily.DailyCreatedEvent;
import com.futspring.backend.domain.daily.DailyFinalizedEvent;
import com.futspring.backend.domain.daily.DiaristaBumpedEvent;
import com.futspring.backend.domain.daily.TeamsSortedEvent;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.pelada.AdminsChangedEvent;
import com.futspring.backend.domain.pelada.MembersAddedEvent;
import com.futspring.backend.domain.pelada.MembersRemovedEvent;
import com.futspring.backend.domain.pelada.MensalistasChangedEvent;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaTransferredEvent;
import com.futspring.backend.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock NotificationRepository notificationRepository;
    @Mock DailyAttendanceRepository attendanceRepository;
    @Mock DailyAttendanceBumpRepository bumpRepository;
    @Mock NotificationDelivery delivery;

    NotificationService service;
    User admin;
    User member;
    User other;
    Pelada pelada;
    Daily daily;
    List<Notification> saved;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, attendanceRepository, bumpRepository, delivery,
                Clock.fixed(NOW, ZoneOffset.UTC));
        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").build();
        other = User.builder().id(3L).email("other@example.com").username("other").password("hash").build();
        pelada = Pelada.builder().id(10L).name("Pelada do Fut").creator(admin)
                .members(new HashSet<>(Set.of(admin, member, other))).build();
        daily = Daily.builder().id(100L).pelada(pelada).dailyDate(LocalDate.of(2026, 10, 31)).dailyTime("20:00").build();

        // saveAll assigns ids, like IDENTITY inserts do, and keeps what was saved
        saved = new ArrayList<>();
        AtomicLong ids = new AtomicLong(1000);
        lenient().when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Notification> rows = inv.getArgument(0);
            rows.forEach(n -> n.setId(ids.incrementAndGet()));
            saved.addAll(rows);
            return rows;
        });
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private List<User> recipients() {
        return saved.stream().map(Notification::getRecipient).toList();
    }

    @Test
    void diaristaBumped_notifiesTheDiaristaWithTheMensalistaAsActor() {
        DailyAttendanceBump bump = DailyAttendanceBump.builder().id(7L).daily(daily).user(member).bumpedBy(admin)
                .bumpedAt(NOW).build();
        when(bumpRepository.findById(7L)).thenReturn(Optional.of(bump));

        service.onDiaristaBumped(new DiaristaBumpedEvent(7L));

        assertThat(saved).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.DIARISTA_BUMPED);
            assertThat(n.getRecipient()).isEqualTo(member);
            assertThat(n.getActor()).isEqualTo(admin);
            assertThat(n.getDaily()).isEqualTo(daily);
            assertThat(n.getPelada()).isEqualTo(pelada);
            assertThat(n.getCreatedAt()).isEqualTo(NOW);
            assertThat(n.getReadAt()).isNull();
        });
    }

    @Test
    void sessionCanceled_notifiesTheConfirmedPlayersExceptTheActor() {
        when(attendanceRepository.findUsersByDaily(daily)).thenReturn(List.of(admin, member, other));

        service.onDailyCanceled(new DailyCanceledEvent(daily, admin));

        assertThat(recipients()).containsExactly(member, other);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.SESSION_CANCELED);
    }

    @Test
    void sessionCanceled_nobodyConfirmed_savesNothing() {
        when(attendanceRepository.findUsersByDaily(daily)).thenReturn(List.of(admin));

        service.onDailyCanceled(new DailyCanceledEvent(daily, admin));

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    void attendanceChangedByAdmin_confirmedAndRemoved() {
        service.onAttendanceChangedByAdmin(new AttendanceChangedByAdminEvent(daily, admin, List.of(admin, member), true));
        service.onAttendanceChangedByAdmin(new AttendanceChangedByAdminEvent(daily, admin, List.of(other), false));

        assertThat(saved).extracting(Notification::getType).containsExactly(
                NotificationType.ATTENDANCE_CONFIRMED_BY_ADMIN, NotificationType.ATTENDANCE_REMOVED_BY_ADMIN);
        assertThat(recipients()).containsExactly(member, other);
    }

    @Test
    void sessionCreated_byAnAdmin_notifiesTheOtherMembers() {
        service.onDailyCreated(new DailyCreatedEvent(daily, admin));

        assertThat(recipients()).containsExactlyInAnyOrder(member, other);
    }

    @Test
    void sessionCreated_byTheScheduler_notifiesEveryMember() {
        service.onDailyCreated(new DailyCreatedEvent(daily, null));

        assertThat(recipients()).containsExactlyInAnyOrder(admin, member, other);
        assertThat(saved).allMatch(n -> n.getActor() == null);
    }

    @Test
    void teamsSorted_collapsesIntoTheUnreadNotificationAndCreatesTheRest() {
        Notification unread = Notification.builder().id(5L).recipient(member).type(NotificationType.TEAMS_SORTED)
                .daily(daily).pelada(pelada).createdAt(NOW.minusSeconds(3600)).build();
        when(notificationRepository.findUnread(eq(NotificationType.TEAMS_SORTED), eq(daily), anyCollection()))
                .thenReturn(List.of(unread));

        service.onTeamsSorted(new TeamsSortedEvent(daily, admin, List.of(admin, member, other)));

        // member's unread one goes back to the top; other (read before or never notified) gets a new one
        assertThat(unread.getCreatedAt()).isEqualTo(NOW);
        assertThat(recipients()).containsExactly(other);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<User>> targets = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(notificationRepository).findUnread(eq(NotificationType.TEAMS_SORTED), eq(daily), targets.capture());
        assertThat(targets.getValue()).containsExactlyInAnyOrder(member, other);
    }

    @Test
    void sessionFinished_firstFinalize_announcesTheSessionAndEveryAward() {
        when(notificationRepository.findUnread(any(), any(), anyCollection())).thenReturn(List.of());

        service.onDailyFinalized(new DailyFinalizedEvent(daily, admin, List.of(admin, member, other), true,
                List.of(member), List.of(other), List.of(admin, member), List.of()));

        List<Notification> finished = saved.stream().filter(n -> n.getType() == NotificationType.SESSION_FINISHED).toList();
        assertThat(finished).extracting(Notification::getRecipient).containsExactlyInAnyOrder(member, other);

        List<Notification> awards = saved.stream().filter(n -> n.getType() == NotificationType.AWARD_WON).toList();
        assertThat(awards).extracting(n -> n.getRecipient().getUsername() + ":" + n.getAward()).containsExactlyInAnyOrder(
                "member:PUSKAS", "other:WILTBALL", "admin:ARTILHEIRO", "member:ARTILHEIRO");
        // awards are system events: the admin who finalized hears about their own award
        assertThat(awards).allMatch(n -> n.getActor() == null);
    }

    @Test
    void sessionFinished_secondFinalize_doesNotAnnounceTheAwardsAgain() {
        when(notificationRepository.findUnread(any(), any(), anyCollection())).thenReturn(List.of());

        service.onDailyFinalized(new DailyFinalizedEvent(daily, admin, List.of(member), false,
                List.of(member), List.of(), List.of(member), List.of()));

        assertThat(saved).extracting(Notification::getType).containsExactly(NotificationType.SESSION_FINISHED);
    }

    @Test
    void membershipEvents_mapToTheirTypes() {
        service.onMembersAdded(new MembersAddedEvent(pelada, admin, List.of(other)));
        service.onMembersRemoved(new MembersRemovedEvent(pelada, admin, List.of(other)));
        service.onAdminsChanged(new AdminsChangedEvent(pelada, admin, List.of(member), true));
        service.onAdminsChanged(new AdminsChangedEvent(pelada, admin, List.of(member), false));
        service.onMensalistasChanged(new MensalistasChangedEvent(pelada, admin, List.of(member), true));
        service.onMensalistasChanged(new MensalistasChangedEvent(pelada, admin, List.of(member), false));
        service.onPeladaTransferred(new PeladaTransferredEvent(pelada, admin, member));

        assertThat(saved).extracting(Notification::getType).containsExactly(
                NotificationType.ADDED_TO_PELADA, NotificationType.REMOVED_FROM_PELADA,
                NotificationType.ADMIN_GRANTED, NotificationType.ADMIN_REVOKED,
                NotificationType.MENSALISTA_GRANTED, NotificationType.MENSALISTA_REVOKED,
                NotificationType.PELADA_TRANSFERRED);
        assertThat(saved).allMatch(n -> n.getPelada() == pelada && n.getDaily() == null && n.getActor() == admin);
    }

    @Test
    void membershipEvent_onTheActorThemselves_savesNothing() {
        service.onAdminsChanged(new AdminsChangedEvent(pelada, admin, List.of(admin), false));
        service.onMensalistasChanged(new MensalistasChangedEvent(pelada, admin, List.of(), true));

        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    void delivery_runsOnlyAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.onMembersAdded(new MembersAddedEvent(pelada, admin, List.of(other)));

        verifyNoInteractions(delivery);
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(delivery).deliver(List.of(saved.get(0).getId()));
    }

    @Test
    void delivery_neverRunsWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        service.onMembersAdded(new MembersAddedEvent(pelada, admin, List.of(other)));

        // a rollback only calls afterCompletion, never afterCommit
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(delivery);
    }

    @Test
    void delivery_noTransaction_registersNothing() {
        service.onMembersAdded(new MembersAddedEvent(pelada, admin, List.of(other)));

        assertThat(saved).hasSize(1);
        verifyNoInteractions(delivery);
    }
}
