package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.notification.*;
import com.futspring.backend.domain.user.*;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.pelada.InvitationStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PeladaInvitationTest extends BaseIntegrationTest {
    static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean Clock clock;
    @Autowired PeladaInvitationService service;
    @Autowired PeladaService peladaService;
    @Autowired PeladaRepository peladas;
    @Autowired UserRepository users;
    @Autowired PeladaInvitationRepository invitations;
    @Autowired PeladaInviteLinkRepository links;
    @Autowired PeladaLinkBlockRepository blocks;
    @Autowired NotificationRepository notifications;
    @Autowired com.futspring.backend.domain.daily.repository.DailyRepository dailies;
    @Autowired com.futspring.backend.domain.daily.repository.DailyAttendanceRepository attendance;
    @Autowired PeladaMensalistaRepository mensalistas;
    User admin, recipient, other;
    Pelada pelada;
    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(NOW); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        admin = user("admin"); recipient = user("recipient"); other = user("other");
        pelada = peladas.save(Pelada.builder().name("Pelada de sábado").dayOfWeek("SATURDAY").timeOfDay("19:00")
                .duration(1f).creator(admin).members(new HashSet<>(Set.of(admin))).admins(new HashSet<>(Set.of(admin))).build());
    }
    User user(String name) { return users.save(User.builder().username(name).email(name + "@invite.test").password("hash").build()); }
    Long invite() { return service.invite(pelada.getId(), recipient.getId(), admin.getEmail()).id(); }
    String link() { return service.replaceLink(pelada.getId(), admin.getEmail()).token(); }
    void fails(Runnable action, HttpStatus status) { assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getStatus()).isEqualTo(status)); }
    long joinedNotices() { return notifications.findAll().stream().filter(n -> n.getType() == NotificationType.MEMBER_JOINED).count(); }
    @Test void pendingDoesNotGrantPrivateAccessAndResendDoesNotExtendExpiry() throws Exception {
        Long id = invite();
        assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), recipient.getId())).isFalse();
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId()).header("Authorization", bearerToken(recipient.getId(), recipient.getEmail()))).andExpect(status().isForbidden());
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        var again = service.invite(pelada.getId(), recipient.getId(), admin.getEmail());
        assertThat(again.id()).isEqualTo(id); assertThat(again.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(invitations.count()).isEqualTo(1);
        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.PELADA_INVITATION); assertThat(n.getInvitationId()).isEqualTo(id);
        });
    }
    @Test void acceptanceIsIdempotentAndJoinsAsDiaristaWithoutAdmin() {
        Long id = invite(); service.accept(id, recipient.getEmail()); service.accept(id, recipient.getEmail());
        assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), recipient.getId())).isTrue();
        assertThat(peladas.existsByIdAndAdmins_Id(pelada.getId(), recipient.getId())).isFalse();
        assertThat(mensalistas.findUserIdsByPelada(pelada)).doesNotContain(recipient.getId());
        assertThat(invitations.findById(id).orElseThrow().getStatus()).isEqualTo(ACCEPTED);
        assertThat(joinedNotices()).isEqualTo(1);
        assertThat(service.list(pelada.getId(), false, 0, 20, admin.getEmail())).singleElement().satisfies(i -> assertThat(i.isMember()).isTrue());
        fails(this::invite, HttpStatus.CONFLICT);
    }
    @Test void membershipIsNotCappedBySessionCapacityAndJoiningNeverConfirmsAttendance() {
        var daily = dailies.save(com.futspring.backend.domain.daily.entity.Daily.builder().pelada(pelada)
                .dailyDate(LocalDate.of(2026, 11, 7)).dailyTime("19:00").build());
        String token = link();
        for (int i = 0; i < 11; i++) service.joinLink(token, user("player" + i).getEmail());
        assertThat(pelada.getMembers()).hasSize(12);
        assertThat(pelada.getMembers().size()).isGreaterThan(pelada.capacity());
        assertThat(attendance.countByDaily(daily)).isZero();
    }
    @Test void historyIsPaginatedIndependentlyFromPendingInvitations() {
        for (int i = 0; i < 3; i++) invitations.save(PeladaInvitation.builder().pelada(pelada).recipient(recipient)
                .sender(admin).status(CANCELED).createdAt(NOW.minusSeconds(i)).expiresAt(NOW.plusSeconds(60))
                .canceledAt(NOW).build());
        Long pending = invite();
        assertThat(service.list(pelada.getId(), false, 0, 2, admin.getEmail())).hasSize(2);
        assertThat(service.list(pelada.getId(), false, 1, 2, admin.getEmail())).hasSize(1);
        assertThat(service.list(pelada.getId(), true, 0, 2, admin.getEmail())).singleElement().satisfies(i -> assertThat(i.id()).isEqualTo(pending));
        assertThat(service.mine(0, 20, other.getEmail())).isEmpty();
    }
    @Test void onlyRecipientCanReadAcceptOrDecline() {
        Long id = invite();
        fails(() -> service.get(id, other.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.accept(id, other.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.decline(id, admin.getEmail()), HttpStatus.FORBIDDEN);
    }
    @Test void exactExpiryPreventsResponsesAndReadsNeverMutateStoredStatus() {
        Long id = invite(); when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(7)));
        assertThat(service.get(id, recipient.getEmail()).status()).isEqualTo(EXPIRED);
        assertThat(service.mine(0, 20, recipient.getEmail())).isEmpty();
        assertThat(service.list(pelada.getId(), false, 0, 20, admin.getEmail())).hasSize(1);
        assertThat(invitations.findById(id).orElseThrow().getStatus()).isEqualTo(PENDING);
        fails(() -> service.accept(id, recipient.getEmail()), HttpStatus.CONFLICT);
        fails(() -> service.decline(id, recipient.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void expiredInvitationCanBeReplacedWithoutLosingHistory() {
        Long id = invite(); when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(7)));
        assertThat(invite()).isNotEqualTo(id); assertThat(invitations.findById(id).orElseThrow().getStatus()).isEqualTo(EXPIRED);
    }
    @Test void refusalCooldownEndsExactlySevenDaysAfterResponse() {
        Long id = invite(); Instant declined = NOW.plusSeconds(60); when(clock.instant()).thenReturn(declined);
        service.decline(id, recipient.getEmail()); service.decline(id, recipient.getEmail());
        when(clock.instant()).thenReturn(declined.plus(Duration.ofDays(7)).minusNanos(1)); fails(this::invite, HttpStatus.CONFLICT);
        when(clock.instant()).thenReturn(declined.plus(Duration.ofDays(7))); assertThat(invite()).isNotEqualTo(id);
        assertThat(joinedNotices()).isZero();
    }
    @Test void joiningByLinkPreservesDeclineAndResolvesPendingWithoutDuplicateNotifications() {
        Long declined = invite(); service.decline(declined, recipient.getEmail()); String token = link(); service.joinLink(token, recipient.getEmail());
        assertThat(invitations.findById(declined).orElseThrow().getStatus()).isEqualTo(DECLINED);
        Long pending = service.invite(pelada.getId(), other.getId(), admin.getEmail()).id();
        service.joinLink(token, other.getEmail()); service.joinLink(token, other.getEmail()); service.accept(pending, other.getEmail());
        assertThat(invitations.findById(pending).orElseThrow().getStatus()).isEqualTo(ACCEPTED);
        assertThat(joinedNotices()).isEqualTo(2);
    }
    @Test void removalBlocksLinkUntilNewIndividualAcceptanceAndRetryNeverRejoinsRemovedMember() {
        Long original = invite(); service.accept(original, recipient.getEmail()); String token = link();
        peladaService.removePlayer(pelada.getId(), recipient.getId(), admin.getEmail());
        assertThat(blocks.existsByPeladaIdAndUserId(pelada.getId(), recipient.getId())).isTrue();
        fails(() -> service.joinLink(token, recipient.getEmail()), HttpStatus.FORBIDDEN);
        service.accept(original, recipient.getEmail()); assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), recipient.getId())).isFalse();
        Long next = invite(); fails(() -> service.joinLink(token, recipient.getEmail()), HttpStatus.FORBIDDEN);
        service.accept(next, recipient.getEmail()); assertThat(blocks.existsByPeladaIdAndUserId(pelada.getId(), recipient.getId())).isFalse();
        assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), recipient.getId())).isTrue();
    }
    @Test void removalCancelsAStalePendingInvitation() {
        Long id = invite(); pelada.getMembers().add(recipient); peladas.saveAndFlush(pelada);
        peladaService.removePlayer(pelada.getId(), recipient.getId(), admin.getEmail());
        assertThat(invitations.findById(id).orElseThrow().getStatus()).isEqualTo(CANCELED);
        fails(() -> service.accept(id, recipient.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void cancellationScopesChildIdAndRejectsLaterResponses() {
        Long id = invite(); Pelada another = peladas.save(Pelada.builder().name("Outra").dayOfWeek("SUNDAY").timeOfDay("18:00").duration(1f)
                .creator(admin).members(new HashSet<>(Set.of(admin))).admins(new HashSet<>(Set.of(admin))).build());
        fails(() -> service.cancel(another.getId(), id, admin.getEmail()), HttpStatus.NOT_FOUND);
        service.cancel(pelada.getId(), id, admin.getEmail()); service.cancel(pelada.getId(), id, admin.getEmail());
        fails(() -> service.accept(id, recipient.getEmail()), HttpStatus.CONFLICT); fails(() -> service.decline(id, recipient.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void nonAdminsCannotManageInvitationsOrLinks() {
        Long id = invite();
        fails(() -> service.invite(pelada.getId(), other.getId(), recipient.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.list(pelada.getId(), null, 0, 20, recipient.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.cancel(pelada.getId(), id, recipient.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.activeLink(pelada.getId(), recipient.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.replaceLink(pelada.getId(), recipient.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> service.revokeLink(pelada.getId(), recipient.getEmail()), HttpStatus.FORBIDDEN);
    }
    @Test void linkIsReconstructedAndReplacementRevocationAndExactExpiryInvalidateIt() {
        String first = link(); assertThat(service.activeLink(pelada.getId(), admin.getEmail()).token()).isEqualTo(first);
        String second = link(); fails(() -> service.preview(first), HttpStatus.GONE); fails(() -> service.joinLink(first, recipient.getEmail()), HttpStatus.GONE);
        service.revokeLink(pelada.getId(), admin.getEmail()); fails(() -> service.joinLink(second, recipient.getEmail()), HttpStatus.GONE);
        String third = link(); when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(7)));
        fails(() -> service.preview(third), HttpStatus.GONE); fails(() -> service.joinLink(third, recipient.getEmail()), HttpStatus.GONE);
        assertThat(service.activeLink(pelada.getId(), admin.getEmail())).isNull();
    }
    @Test void changingAdminDoesNotInvalidateTheirInvitationsOrLinkAndJoinNotifiesOnlyCurrentAdmins() {
        pelada.getMembers().add(other); pelada.getAdmins().add(other); peladas.saveAndFlush(pelada);
        Long id = service.invite(pelada.getId(), recipient.getId(), other.getEmail()).id();
        String token = service.replaceLink(pelada.getId(), other.getEmail()).token(); peladaService.setAdmin(pelada.getId(), other.getId(), false, admin.getEmail());
        assertThat(service.activeLink(pelada.getId(), admin.getEmail()).token()).isEqualTo(token); service.accept(id, recipient.getEmail());
        assertThat(notifications.findAll().stream().filter(n -> n.getType() == NotificationType.MEMBER_JOINED).map(n -> n.getRecipient().getId()).toList()).containsExactly(admin.getId());
    }
    @Test void deletionCleansAllInvitationData() {
        Long id = invite(); service.accept(id, recipient.getEmail()); peladaService.removePlayer(pelada.getId(), recipient.getId(), admin.getEmail()); link();
        entityManager.flush(); entityManager.clear();
        peladaService.deletePelada(pelada.getId(), admin.getEmail()); peladas.flush();
        assertThat(invitations.count()).isZero(); assertThat(links.count()).isZero(); assertThat(blocks.count()).isZero(); assertThat(notifications.count()).isZero();
    }
    @Test void sharedCreationQuotaDoesNotCountPendingResends() {
        invite(); for (int i = 0; i < 9; i++) link(); fails(this::link, HttpStatus.TOO_MANY_REQUESTS);
        assertThat(service.invite(pelada.getId(), recipient.getId(), admin.getEmail()).status()).isEqualTo(PENDING);
    }
    @Test void publicPreviewIgnoresStaleSessionAndNeverJoinsButPostRequiresAuthentication() throws Exception {
        String token = link(); mockMvc.perform(get("/api/v1/invite-links/" + token).header("Authorization", "Bearer stale"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(pelada.getName())).andExpect(jsonPath("$.members").doesNotExist()).andExpect(jsonPath("$.address").doesNotExist());
        assertThat(pelada.getMembers()).containsExactly(admin);
        mockMvc.perform(post("/api/v1/invite-links/" + token + "/join")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/invite-links/" + token + "/join").header("Authorization", bearerToken(recipient.getId(), recipient.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.peladaId").value(pelada.getId()));
    }
    @Test void publicQuotaIsPerIpAcrossDifferentTokens() throws Exception {
        for (int i = 0; i < 60; i++) mockMvc.perform(get("/api/v1/invite-links/invalid" + i).with(r -> { r.setRemoteAddr("192.0.2.44"); return r; })).andExpect(status().isGone());
        mockMvc.perform(get("/api/v1/invite-links/other").with(r -> { r.setRemoteAddr("192.0.2.44"); return r; })).andExpect(status().isTooManyRequests());
    }
    @Test void validatesRequestMissingInvitationAndRemovedEndpoint() throws Exception {
        String auth = bearerToken(admin.getId(), admin.getEmail());
        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/invitations").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":null}")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/invitations/999999").header("Authorization", auth)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/players").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + recipient.getId() + "}")).andExpect(status().isNotFound());
    }
}
