package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.*;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real transactions and PostgreSQL row locks, including Flyway's partial unique indexes. */
@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class InvitationConcurrencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword); r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    @Autowired PeladaInvitationService service;
    @Autowired PeladaService peladaService;
    @Autowired PeladaRepository peladas;
    @Autowired PeladaInvitationRepository invitations;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    record Fixture(User admin, User player, Long peladaId) { }
    Fixture fixture() {
        String key = UUID.randomUUID().toString();
        User admin = users.save(User.builder().email(key + "a@test.com").username("Admin").password("hash").build());
        User player = users.save(User.builder().email(key + "p@test.com").username("Player").password("hash").build());
        Pelada p = peladas.save(Pelada.builder().name("Concorrência").dayOfWeek("SUNDAY").timeOfDay("18:00").duration(1f)
                .creator(admin).members(new HashSet<>(Set.of(admin))).admins(new HashSet<>(Set.of(admin))).build());
        return new Fixture(admin, player, p.getId());
    }
    List<HttpStatus> race(Runnable first, Runnable second) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<HttpStatus>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) futures.add(pool.submit(() -> {
                start.await();
                try { action.run(); return HttpStatus.OK; } catch (AppException e) { return e.getStatus(); }
            }));
            start.countDown();
            List<HttpStatus> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(20, TimeUnit.SECONDS));
            return result;
        }
    }
    long joined(Fixture f) { return jdbc.queryForObject("select count(*) from notifications where pelada_id=? and type='MEMBER_JOINED'", Long.class, f.peladaId()); }
    @Test void twoAcceptsProduceOneMemberAndOneNotificationSet() throws Exception {
        var f = fixture(); Long id = service.invite(f.peladaId(), f.player().getId(), f.admin().getEmail()).id();
        assertThat(race(() -> service.accept(id, f.player().getEmail()), () -> service.accept(id, f.player().getEmail())))
                .containsExactly(HttpStatus.OK, HttpStatus.OK);
        assertThat(peladas.existsByIdAndMembers_Id(f.peladaId(), f.player().getId())).isTrue();
        assertThat(joined(f)).isEqualTo(1);
    }
    @Test void acceptRacingCancellationHasOneConsistentWinner() throws Exception {
        var f = fixture(); Long id = service.invite(f.peladaId(), f.player().getId(), f.admin().getEmail()).id();
        assertThat(race(() -> service.accept(id, f.player().getEmail()), () -> service.cancel(f.peladaId(), id, f.admin().getEmail())))
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        boolean accepted = invitations.findById(id).orElseThrow().getStatus() == InvitationStatus.ACCEPTED;
        assertThat(peladas.existsByIdAndMembers_Id(f.peladaId(), f.player().getId())).isEqualTo(accepted);
        assertThat(joined(f)).isEqualTo(accepted ? 1 : 0);
    }
    @Test void joiningRacingRevocationCannotUseARevokedLink() throws Exception {
        var f = fixture(); String token = service.replaceLink(f.peladaId(), f.admin().getEmail()).token();
        var result = race(() -> service.joinLink(token, f.player().getEmail()), () -> service.revokeLink(f.peladaId(), f.admin().getEmail()));
        assertThat(result.get(1)).isEqualTo(HttpStatus.OK);
        assertThat(result.get(0)).isIn(HttpStatus.OK, HttpStatus.GONE);
        assertThat(peladas.existsByIdAndMembers_Id(f.peladaId(), f.player().getId())).isEqualTo(result.get(0) == HttpStatus.OK);
        assertThatThrownBy(() -> service.joinLink(token, f.player().getEmail())).isInstanceOf(AppException.class);
    }
    @Test void replacementAndJoiningSerializeOnThePelada() throws Exception {
        var f = fixture(); String token = service.replaceLink(f.peladaId(), f.admin().getEmail()).token();
        var result = race(() -> service.joinLink(token, f.player().getEmail()), () -> service.replaceLink(f.peladaId(), f.admin().getEmail()));
        assertThat(result.get(1)).isEqualTo(HttpStatus.OK); assertThat(result.get(0)).isIn(HttpStatus.OK, HttpStatus.GONE);
        assertThat(joined(f)).isEqualTo(result.get(0) == HttpStatus.OK ? 1 : 0);
        assertThat(service.activeLink(f.peladaId(), f.admin().getEmail()).token()).isNotEqualTo(token);
    }
    @Test void removalAndLinkRetryNeverReaddARemovedMember() throws Exception {
        var f = fixture(); String token = service.replaceLink(f.peladaId(), f.admin().getEmail()).token(); service.joinLink(token, f.player().getEmail());
        var result = race(() -> service.joinLink(token, f.player().getEmail()), () -> peladaService.removePlayer(f.peladaId(), f.player().getId(), f.admin().getEmail()));
        assertThat(result.get(1)).isEqualTo(HttpStatus.OK); assertThat(result.get(0)).isIn(HttpStatus.OK, HttpStatus.FORBIDDEN);
        assertThat(peladas.existsByIdAndMembers_Id(f.peladaId(), f.player().getId())).isFalse();
        assertThat(joined(f)).isEqualTo(1);
    }
    @Test void acceptAndRemovalHaveConsistentMembershipAndBlock() throws Exception {
        var f = fixture(); Long id = service.invite(f.peladaId(), f.player().getId(), f.admin().getEmail()).id();
        var result = race(() -> service.accept(id, f.player().getEmail()), () -> peladaService.removePlayer(f.peladaId(), f.player().getId(), f.admin().getEmail()));
        assertThat(result.get(0)).isEqualTo(HttpStatus.OK); assertThat(result.get(1)).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        assertThat(peladas.existsByIdAndMembers_Id(f.peladaId(), f.player().getId())).isEqualTo(result.get(1) == HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select count(*) from pelada_link_blocks where pelada_id=?", Long.class, f.peladaId())).isEqualTo(result.get(1) == HttpStatus.OK ? 1 : 0);
    }
    @Test void pendingInvitationAndActiveLinkUniquenessAreEnforcedByPostgres() {
        var f = fixture(); Long id = service.invite(f.peladaId(), f.player().getId(), f.admin().getEmail()).id(); service.replaceLink(f.peladaId(), f.admin().getEmail());
        assertThatThrownBy(() -> jdbc.update("""
            insert into pelada_invitations(pelada_id, recipient_id, sender_id, status, created_at, expires_at)
            select pelada_id, recipient_id, sender_id, status, created_at, expires_at from pelada_invitations where id=?
            """, id)).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("""
            insert into pelada_invite_links(pelada_id, creator_id, created_at, expires_at)
            select pelada_id, creator_id, created_at, expires_at from pelada_invite_links where pelada_id=?
            """, f.peladaId())).isInstanceOf(DuplicateKeyException.class);
    }
}
