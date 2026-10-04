package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
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

@Service
@RequiredArgsConstructor
public class DailyAttendanceService {

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyRepository dailyRepository;
    private final UserRepository userRepository;
    private final DailyListItemAssembler dailyListItemAssembler;

    @Transactional
    public DailyListItemDTO confirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        accessHelper.requireMember(daily.getPelada(), caller);
        requireUnlocked(daily);

        if (daily.getConfirmedPlayers().contains(caller)) {
            throw new AppException(HttpStatus.CONFLICT, "Você já confirmou presença nesta sessão");
        }

        daily.getConfirmedPlayers().add(caller);
        dailyRepository.save(daily);
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    @Transactional
    public DailyListItemDTO disconfirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Daily daily = findDaily(id);
        requireUnlocked(daily);

        if (!daily.getConfirmedPlayers().contains(caller)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Você não confirmou presença nesta sessão");
        }

        daily.getConfirmedPlayers().remove(caller);
        dailyRepository.save(daily);
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

        if (daily.getConfirmedPlayers().contains(target)) {
            throw new AppException(HttpStatus.CONFLICT, "O jogador já está confirmado nesta sessão");
        }

        daily.getConfirmedPlayers().add(target);
        dailyRepository.save(daily);
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

        if (!daily.getConfirmedPlayers().contains(target)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "O jogador não está confirmado nesta sessão");
        }

        daily.getConfirmedPlayers().remove(target);
        dailyRepository.save(daily);
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

        daily.getConfirmedPlayers().addAll(pelada.getMembers());
        dailyRepository.save(daily);
        return dailyListItemAssembler.toListItem(daily, caller);
    }

    void clearAttendees(Daily daily) {
        daily.getConfirmedPlayers().clear();
        dailyRepository.save(daily);
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
