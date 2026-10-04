package com.futspring.backend.service;

import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class DailyAttendanceService {

    private final UserAuthenticationHelper userAuthHelper;
    private final DailyRepository dailyRepository;
    private final UserRepository userRepository;

    private static final Set<String> LOCKED_STATUSES = Set.of("IN_COURSE", "FINISHED", "CANCELED");

    @Transactional
    public DailyListItemDTO confirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot confirm attendance for a daily with status " + daily.getStatus());
        }

        if (daily.getConfirmedPlayers().contains(caller)) {
            throw new AppException(HttpStatus.CONFLICT, "You are already confirmed for this daily");
        }

        daily.getConfirmedPlayers().add(caller);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public DailyListItemDTO disconfirmAttendance(Long id, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Daily daily = dailyRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot disconfirm attendance for a daily with status " + daily.getStatus());
        }

        if (!daily.getConfirmedPlayers().contains(caller)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "You are not confirmed for this daily");
        }

        daily.getConfirmedPlayers().remove(caller);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public DailyListItemDTO adminConfirmAttendance(Long dailyId, Long targetUserId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        Daily daily = dailyRepository.findById(dailyId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can confirm attendance for other members");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Target user not found"));

        if (!pelada.getMembers().contains(targetUser)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Target user is not a member of this pelada");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot confirm attendance for a daily with status " + daily.getStatus());
        }

        if (daily.getConfirmedPlayers().contains(targetUser)) {
            throw new AppException(HttpStatus.CONFLICT, "Player is already confirmed for this daily");
        }

        daily.getConfirmedPlayers().add(targetUser);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    @Transactional
    public DailyListItemDTO adminDisconfirmAttendance(Long dailyId, Long targetUserId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        Daily daily = dailyRepository.findById(dailyId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));

        Pelada pelada = daily.getPelada();
        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can disconfirm attendance for other members");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Target user not found"));

        if (!pelada.getMembers().contains(targetUser)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Target user is not a member of this pelada");
        }

        if (LOCKED_STATUSES.contains(daily.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot disconfirm attendance for a daily with status " + daily.getStatus());
        }

        if (!daily.getConfirmedPlayers().contains(targetUser)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Player is not confirmed for this daily");
        }

        daily.getConfirmedPlayers().remove(targetUser);
        dailyRepository.save(daily);
        return DailyListItemDTO.from(daily);
    }

    void clearAttendees(Daily daily) {
        daily.getConfirmedPlayers().clear();
        dailyRepository.save(daily);
    }
}
