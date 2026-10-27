package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;
    private final DailyListItemAssembler dailyListItemAssembler;

    @Transactional
    public DailyListItemDTO confirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireMember(daily.getPelada(), caller);
        requireUnlocked(daily);

        if (attendanceRepository.existsByDailyAndUser(daily, caller)) {
            throw new AppException(HttpStatus.CONFLICT, "Você já confirmou presença nesta sessão");
        }

        attendanceRepository.save(new DailyAttendance(daily, caller, Instant.now()));
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
        Daily daily = findDaily(dailyId);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        User target = findMember(pelada, targetUserId);
        requireUnlocked(daily);

        if (attendanceRepository.existsByDailyAndUser(daily, target)) {
            throw new AppException(HttpStatus.CONFLICT, "O jogador já está confirmado nesta sessão");
        }

        attendanceRepository.save(new DailyAttendance(daily, target, Instant.now()));
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

    /** Confirms every member who hasn't confirmed yet ("Confirmar todos"). */
    @Transactional
    public DailyListItemDTO adminConfirmAll(Long dailyId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Daily daily = findDaily(dailyId);
        Pelada pelada = daily.getPelada();
        accessHelper.requireAdmin(pelada, caller);
        requireUnlocked(daily);

        Set<User> confirmed = Set.copyOf(attendanceRepository.findUsersByDaily(daily));
        Instant now = Instant.now();
        List<DailyAttendance> added = pelada.getMembers().stream()
                .filter(member -> !confirmed.contains(member))
                .map(member -> new DailyAttendance(daily, member, now))
                .toList();
        attendanceRepository.saveAll(added);
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    void clearAttendees(Daily daily) {
        attendanceRepository.deleteAllOfDaily(daily);
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
