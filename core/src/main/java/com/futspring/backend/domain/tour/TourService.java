package com.futspring.backend.domain.tour;

import com.futspring.backend.domain.tour.dto.TourStateDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;

// The caller's guided tours; every method reads the caller from the token
@Service
@RequiredArgsConstructor
public class TourService {

    private final UserTourRepository tourRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TourStateDTO get(String email) {
        User caller = userAuthHelper.getAuthenticatedUser(email);
        return new TourStateDTO(tourRepository.findByIdUserId(caller.getId()).stream()
                .map(UserTour::getTour)
                .sorted(Comparator.naturalOrder())
                .toList());
    }

    // Idempotent: the first time is kept
    @Transactional
    public void markSeen(TourKey tour, String email) {
        User caller = userAuthHelper.getAuthenticatedUser(email);
        UserTourId id = new UserTourId(caller.getId(), tour);
        if (!tourRepository.existsById(id)) {
            tourRepository.save(new UserTour(caller, tour, Instant.now(clock)));
        }
    }
}
