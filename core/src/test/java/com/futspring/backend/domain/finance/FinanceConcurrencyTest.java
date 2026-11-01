package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import com.futspring.backend.domain.pelada.*;
import com.futspring.backend.domain.user.*;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class FinanceConcurrencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword); r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    @Autowired FinanceCollectionService collections;
    @Autowired FinanceLedgerService ledger;
    @Autowired FinanceReadService reads;
    @Autowired FinanceMovementRepository movements;
    @Autowired FinanceNoticeRepository notices;
    @Autowired FinanceAccountRepository accounts;
    @Autowired FinanceListRepository lists;
    @Autowired FinancePersonRepository people;
    @MockitoSpyBean FinanceAuditRepository audits;
    @Autowired FinanceFiles files;
    @Autowired PeladaRepository peladas;
    @Autowired UserRepository users;
    record Fixture(User admin, User player, Long pelada, Long list, Long charge) {}
    Fixture fixture() {
        String key = UUID.randomUUID().toString();
        User admin = users.save(User.builder().email(key + "a@finance.test").username("Gestor" + key).password("hash").build());
        User player = users.save(User.builder().email(key + "p@finance.test").username("Jogador" + key).password("hash").build());
        Pelada p = peladas.save(Pelada.builder().name("Concorrência financeira").dayOfWeek("SUNDAY").timeOfDay("18:00").duration(1f)
                .creator(admin).members(new HashSet<>(Set.of(admin, player))).admins(new HashSet<>(Set.of(admin))).build());
        Long list = collections.collection(p.getId(), null, new FinanceRequests.Collection(new FinanceRequests.Command(0L, UUID.randomUUID(), null), "Diária", Classification.DAILY, "", null, null,
                List.of(new FinanceRequests.Component("Campo", "45.00")), null, null, null), admin.getEmail()).detail().collection().id();
        Long charge = collections.participants(p.getId(), list, new FinanceRequests.Participants(command(p.getId(), admin), List.of(player.getId()), List.of()), admin.getEmail()).detail().charges().getFirst().id();
        collections.transition(p.getId(), list, "PUBLISH", command(p.getId(), admin), admin.getEmail());
        return new Fixture(admin, player, p.getId(), list, charge);
    }
    FinanceRequests.Command command(Long id, User admin) { return new FinanceRequests.Command(reads.overview(id, admin.getEmail(), 0, 50).revision(), UUID.randomUUID(), "Teste de correção"); }
    List<HttpStatus> race(Runnable a, Runnable b) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1); List<Future<HttpStatus>> futures = new ArrayList<>();
            for (var action : List.of(a, b)) futures.add(pool.submit(() -> { start.await(); try { action.run(); return HttpStatus.OK; } catch (AppException e) { return e.getStatus(); } }));
            start.countDown(); List<HttpStatus> result = new ArrayList<>(); for (var f : futures) result.add(f.get(30, TimeUnit.SECONDS)); return result;
        }
    }
    FinanceRequests.CreateCollection creation(Fixture f, List<Long> players, boolean publish) {
        return new FinanceRequests.CreateCollection(
                new FinanceRequests.Collection(command(f.pelada(), f.admin()), "Nova diária", Classification.DAILY, "", null, null,
                        List.of(new FinanceRequests.Component("Valor por pessoa", "45.00")), null, null, null),
                players, List.of("Convidado"), publish);
    }
    @Test void concurrentGuidedCreationCommitsOneCompletePublishedList() throws Exception {
        var f = fixture(); var request = creation(f, List.of(f.player().getId()), true);
        long revision = reads.overview(f.pelada(), f.admin().getEmail(), 0, 50).revision();
        Runnable action = () -> collections.createCollection(f.pelada(), request, f.admin().getEmail());
        assertThat(race(action, action)).containsExactly(HttpStatus.OK, HttpStatus.OK);
        var created = lists.findByPeladaId(f.pelada()).stream().filter(l -> !l.getId().equals(f.list())).toList();
        assertThat(created).hasSize(1);
        var detail = reads.detail(f.pelada(), created.getFirst().getId(), f.admin().getEmail());
        assertThat(detail.collection().status()).isEqualTo(ListStatus.OPEN);
        assertThat(detail.charges()).hasSize(2);
        assertThat(reads.overview(f.pelada(), f.admin().getEmail(), 0, 50).revision()).isEqualTo(revision + 1);
        assertThat(audits.findByPeladaId(f.pelada()).stream().filter(a -> a.getRequestKey().equals(request.collection().command().requestKey()))).hasSize(1);
    }
    @Test void guidedCreationRollsBackListPeopleAndRevisionOnInvalidParticipantOrAuditFailure() {
        var f = fixture(); var foreign = fixture();
        int beforeLists = lists.findByPeladaId(f.pelada()).size();
        int beforePeople = people.findByPeladaId(f.pelada()).size();
        int beforeAudits = audits.findByPeladaId(f.pelada()).size();
        long revision = reads.overview(f.pelada(), f.admin().getEmail(), 0, 50).revision();
        assertThatThrownBy(() -> collections.createCollection(f.pelada(), creation(f, List.of(foreign.player().getId()), true), f.admin().getEmail()))
                .isInstanceOf(AppException.class);
        var empty = new FinanceRequests.CreateCollection(creation(f, List.of(), true).collection(), List.of(), List.of(), true);
        assertThatThrownBy(() -> collections.createCollection(f.pelada(), empty, f.admin().getEmail())).isInstanceOf(AppException.class);
        var valid = creation(f, List.of(f.player().getId()), true);
        doThrow(new IllegalStateException("forced audit failure")).when(audits).save(any(FinanceAudit.class));
        try {
            assertThatThrownBy(() -> collections.createCollection(f.pelada(), valid, f.admin().getEmail())).isInstanceOf(IllegalStateException.class);
        } finally { reset(audits); }
        assertThat(lists.findByPeladaId(f.pelada())).hasSize(beforeLists);
        assertThat(people.findByPeladaId(f.pelada())).hasSize(beforePeople);
        assertThat(audits.findByPeladaId(f.pelada())).hasSize(beforeAudits);
        assertThat(reads.overview(f.pelada(), f.admin().getEmail(), 0, 50).revision()).isEqualTo(revision);
        var retry = collections.createCollection(f.pelada(), valid, f.admin().getEmail());
        assertThat(retry.detail().charges()).hasSize(2);
    }
    @Test void retryingTheSameReceiptCommitsOneMovementAndOneAudit() throws Exception {
        var f = fixture(); var request = new FinanceRequests.Receipt(command(f.pelada(), f.admin()), null, null, PaymentMethod.PIX);
        Runnable action = () -> ledger.receive(f.pelada(), f.list(), f.charge(), request, f.admin().getEmail());
        assertThat(race(action, action)).containsExactly(HttpStatus.OK, HttpStatus.OK);
        assertThat(movements.findByChargeIdIn(List.of(f.charge()))).hasSize(1);
        assertThat(audits.findByPeladaId(f.pelada()).stream().filter(a -> a.getRequestKey().equals(request.command().requestKey()))).hasSize(1);
    }
    @Test void competingReceiptsCannotOverwriteAStaleRevisionOrOverpay() throws Exception {
        var f = fixture(); var a = new FinanceRequests.Receipt(command(f.pelada(), f.admin()), "30.00", null, PaymentMethod.CASH);
        var b = new FinanceRequests.Receipt(command(f.pelada(), f.admin()), "30.00", null, PaymentMethod.PIX);
        assertThat(race(() -> ledger.receive(f.pelada(), f.list(), f.charge(), a, f.admin().getEmail()), () -> ledger.receive(f.pelada(), f.list(), f.charge(), b, f.admin().getEmail())))
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(reads.detail(f.pelada(), f.list(), f.admin().getEmail()).charges().getFirst().received()).isEqualTo("30.00");
    }
    @Test void duplicateConcurrentConfirmationHasOneReceiptEvenWithDifferentRequestKeys() throws Exception {
        var f = fixture(); ledger.report(f.pelada(), f.list(), f.charge(), command(f.pelada(), f.admin()), null, f.player().getEmail());
        Long notice = reads.detail(f.pelada(), f.list(), f.admin().getEmail()).charges().getFirst().notices().getFirst().id();
        var a = new FinanceRequests.Confirmation(command(f.pelada(), f.admin()), null, PaymentMethod.PIX);
        var b = new FinanceRequests.Confirmation(command(f.pelada(), f.admin()), null, PaymentMethod.PIX);
        assertThat(race(() -> ledger.confirm(f.pelada(), f.list(), f.charge(), notice, a, f.admin().getEmail()), () -> ledger.confirm(f.pelada(), f.list(), f.charge(), notice, b, f.admin().getEmail())))
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(movements.findByChargeIdIn(List.of(f.charge()))).hasSize(1);
    }
    @Test void auditFailureRollsBackCashRevisionNoticeAndNewFile() throws Exception {
        var f = fixture(); long revision = reads.overview(f.pelada(), f.admin().getEmail(), 0, 50).revision();
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "proof.png", "image/png", bytes.toByteArray());
        Files.createDirectories(files.directory(false));
        Set<String> before;
        try (var paths = Files.list(files.directory(false))) { before = paths.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()); }
        var receipt = new FinanceRequests.Receipt(command(f.pelada(), f.admin()), null, null, PaymentMethod.CASH);
        var report = command(f.pelada(), f.admin());
        doThrow(new IllegalStateException("forced audit failure")).when(audits).save(any(FinanceAudit.class));
        try {
            assertThatThrownBy(() -> ledger.receive(f.pelada(), f.list(), f.charge(), receipt, f.admin().getEmail())).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ledger.report(f.pelada(), f.list(), f.charge(), report, file, f.player().getEmail())).isInstanceOf(IllegalStateException.class);
        } finally { reset(audits); }
        assertThat(movements.findByChargeIdIn(List.of(f.charge()))).isEmpty();
        assertThat(notices.findByChargeIdInOrderByIdDesc(List.of(f.charge()))).isEmpty();
        assertThat(reads.overview(f.pelada(), f.admin().getEmail(), 0, 50).revision()).isEqualTo(revision);
        try (var paths = Files.list(files.directory(false))) { assertThat(paths.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())).isEqualTo(before); }
    }
}
