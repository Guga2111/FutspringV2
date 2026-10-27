package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DailyAttendanceService {

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyRepository dailyRepository;
    private final DailyAttendanceRepository attendanceRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final UserRepository userRepository;
    private final DailyListItemAssembler dailyListItemAssembler;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * The caller confirms themselves. Mensalistas any time (while the session isn't locked); diaristas only from
     * 24 h before the start, in the pelada's zone, and while there are spots. A mensalista who confirms into a
     * full session takes the spot of the diarista confirmed last (or joins over capacity when there is none).
     * Everything is decided here from stored data and the server clock; the request carries nothing time-related.
     */
    @Transactional
    public DailyListItemDTO confirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDailyForUpdate(id);
        Pelada pelada = daily.getPelada();
        accessHelper.requireMember(pelada, caller);
        requireUnlocked(daily);

        if (attendanceRepository.existsByDailyAndUser(daily, caller)) {
            throw new AppException(HttpStatus.CONFLICT, "Você já confirmou presença nesta sessão");
        }

        Instant now = Instant.now(clock);
        boolean isMensalista = mensalistaRepository.existsByPeladaAndUser(pelada, caller);
        if (!isMensalista && now.isBefore(AttendanceWindow.diaristaOpensAt(daily))) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Diaristas podem confirmar a partir de " + AttendanceWindow.formatOpening(daily));
        }

        if (attendanceRepository.countByDaily(daily) >= pelada.capacity()) {
            if (!isMensalista) {
                throw new AppException(HttpStatus.CONFLICT, "Sessão lotada");
            }
            attendanceRepository.findBumpVictim(daily).ifPresent(victim -> bump(victim, caller, now));
        }

        attendanceRepository.save(new DailyAttendance(daily, caller, now));
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    @Transactional
    public DailyListItemDTO disconfirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        requireUnlocked(daily);

        if (attendanceRepository.deleteByDailyAndUser(daily, caller) == 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Você não confirmou presença nesta sessão");
        }

        return dailyListItemAssembler.toListItem(daily, caller);
    }

    @Transactional
    public DailyListItemDTO adminConfirmAttendance(Long dailyId, Long targetUserId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Daily daily = findDailyForUpdate(dailyId);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        User target = findMember(pelada, targetUserId);
        requireUnlocked(daily);

        if (attendanceRepository.existsByDailyAndUser(daily, target)) {
            throw new AppException(HttpStatus.CONFLICT, "O jogador já está confirmado nesta sessão");
        }

        // Admins skip the window and the capacity, and never bump anyone
        attendanceRepository.save(new DailyAttendance(daily, target, Instant.now(clock)));
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    @Transactional
    public DailyListItemDTO adminDisconfirmAttendance(Long dailyId, Long targetUserId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Daily daily = findDaily(dailyId);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        User target = findMember(pelada, targetUserId);
        requireUnlocked(daily);

        if (attendanceRepository.deleteByDailyAndUser(daily, target) == 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "O jogador não está confirmado nesta sessão");
        }

        return dailyListItemAssembler.toListItem(daily, caller);
    }

    /** Confirms every member who hasn't confirmed yet ("Confirmar todos"), past the capacity if needed. */
    @Transactional
    public DailyListItemDTO adminConfirmAll(Long dailyId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Daily daily = findDailyForUpdate(dailyId);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        requireUnlocked(daily);

        Set<User> confirmed = Set.copyOf(attendanceRepository.findUsersByDaily(daily));
        Instant now = Instant.now(clock);
        List<DailyAttendance> added = pelada.getMembers().stream()
                .filter(member -> !confirmed.contains(member))
                .map(member -> new DailyAttendance(daily, member, now))
                .toList();
        attendanceRepository.saveAll(added);
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    /** The caller dismisses the "you lost your spot" alert of this session; other users' bumps are untouched. */
    @Transactional
    public void markBumpsSeen(Long dailyId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(dailyId);
        accessHelper.requireMember(daily.getPelada(), caller);
        bumpRepository.markSeen(daily, caller, Instant.now(clock));
    }

    void clearAttendees(Daily daily) {
        attendanceRepository.deleteAllOfDaily(daily);
    }

    // The diarista loses the spot; the bump is recorded for the in-app alert and the e-mail goes after the commit
    private void bump(DailyAttendance victim, User mensalista, Instant now) {
        attendanceRepository.delete(victim);
        DailyAttendanceBump bump = bumpRepository.save(DailyAttendanceBump.builder()
                .daily(victim.getDaily())
                .user(victim.getUser())
                .bumpedBy(mensalista)
                .bumpedAt(now)
                .build());
        eventPublisher.publishEvent(new DiaristaBumpedEvent(bump.getId()));
    }

    private Daily findDailyForUpdate(Long id) {
        return dailyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }

    private Daily findDaily(Long id) {
        return dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sessão não encontrada"));
    }

    private User findMember(Pelada pelada, Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Jogador não encontrado"));
        if (!accessHelper.isMember(pelada, target)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "O jogador não é membro desta pelada");
        }
        return target;
    }

    private static void requireUnlocked(Daily daily) {
        if (daily.getStatus().isLocked()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Não é possível alterar a presença de uma sessão com status " + daily.getStatus());
        }
    }
}
