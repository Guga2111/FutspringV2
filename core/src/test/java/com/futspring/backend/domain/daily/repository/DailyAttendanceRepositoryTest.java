package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalista;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DailyAttendanceRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PeladaMensalistaRepository mensalistaRepository;

    private User ana;
    private User bia;
    private Pelada pelada;
    private Daily first;
    private Daily second;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder().email("ana@test.com").username("ana").password("hash").build());
        bia = userRepository.save(User.builder().email("bia@test.com").username("bia").password("hash").build());
        pelada = peladaRepository.save(Pelada.builder()
                .name("P").dayOfWeek("FRIDAY").timeOfDay("18:00").duration(1f).creator(ana).build());
        first = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2026, 10, 6)).dailyTime("18:00").build());
        second = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2026, 10, 13)).dailyTime("18:00").build());
    }

    @Test
    void findUsersByDaily_inConfirmationOrder() {
        attendanceRepository.saveAndFlush(new DailyAttendance(first, ana, T0.plusSeconds(60)));
        attendanceRepository.saveAndFlush(new DailyAttendance(first, bia, T0));

        assertThat(attendanceRepository.findUsersByDaily(first)).containsExactly(bia, ana);
        assertThat(attendanceRepository.findByDailyWithUser(first).get(0).getConfirmedAt()).isEqualTo(T0);
    }

    @Test
    void countsAndCallerAttendance_groupedPerDaily() {
        attendanceRepository.saveAndFlush(new DailyAttendance(first, ana, T0));
        attendanceRepository.saveAndFlush(new DailyAttendance(first, bia, T0));
        attendanceRepository.saveAndFlush(new DailyAttendance(second, bia, T0));

        List<Long> ids = List.of(first.getId(), second.getId());
        assertThat(attendanceRepository.countConfirmedByIds(ids))
                .extracting(row -> row[0] + ":" + row[1])
                .containsExactlyInAnyOrder(first.getId() + ":2", second.getId() + ":1");
        assertThat(attendanceRepository.findConfirmedDailyIds(ids, ana.getId())).containsExactly(first.getId());
        assertThat(attendanceRepository.countByDaily(first)).isEqualTo(2);
        assertThat(attendanceRepository.existsByDailyAndUser(second, ana)).isFalse();
    }

    @Test
    void deletes_byPlayerAndBySession() {
        attendanceRepository.saveAndFlush(new DailyAttendance(first, ana, T0));
        attendanceRepository.saveAndFlush(new DailyAttendance(first, bia, T0));
        attendanceRepository.saveAndFlush(new DailyAttendance(second, ana, T0));

        assertThat(attendanceRepository.deleteByDailyAndUser(first, ana)).isEqualTo(1);
        assertThat(attendanceRepository.deleteByDailyAndUser(first, ana)).isZero();
        attendanceRepository.deleteAllOfDaily(second);

        assertThat(attendanceRepository.findUsersByDaily(first)).containsExactly(bia);
        assertThat(attendanceRepository.countByDaily(second)).isZero();
    }

    @Test
    void findBumpVictim_isTheDiaristaConfirmedLast_mensalistasAreNeverVictims() {
        User cid = user("cid");
        attendanceRepository.saveAndFlush(new DailyAttendance(first, ana, T0));
        attendanceRepository.saveAndFlush(new DailyAttendance(first, bia, T0.plusSeconds(60)));
        // cid confirmed last but is a mensalista now: the type is read when the bump happens
        attendanceRepository.saveAndFlush(new DailyAttendance(first, cid, T0.plusSeconds(120)));
        mensalistaRepository.saveAndFlush(PeladaMensalista.builder().pelada(pelada).user(cid).build());

        assertThat(attendanceRepository.findBumpVictim(first)).get().extracting(DailyAttendance::getUser).isEqualTo(bia);
    }

    @Test
    void findBumpVictim_tieOnConfirmedAt_highestUserIdLoses() {
        attendanceRepository.saveAndFlush(new DailyAttendance(first, ana, T0));
        attendanceRepository.saveAndFlush(new DailyAttendance(first, bia, T0));
        User higher = ana.getId() > bia.getId() ? ana : bia;

        assertThat(attendanceRepository.findBumpVictim(first)).get().extracting(DailyAttendance::getUser).isEqualTo(higher);
    }

    @Test
    void findBumpVictim_onlyMensalistasConfirmed_isEmpty() {
        attendanceRepository.saveAndFlush(new DailyAttendance(first, ana, T0));
        mensalistaRepository.saveAndFlush(PeladaMensalista.builder().pelada(pelada).user(ana).build());

        assertThat(attendanceRepository.findBumpVictim(first)).isEmpty();
    }

    private User user(String name) {
        return userRepository.save(User.builder().email(name + "@test.com").username(name).password("hash").build());
    }
}
