package com.futspring.backend.domain.tour;

import com.futspring.backend.domain.tour.dto.TourStateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// The caller's guided tours only; no route takes a userId
@RestController
@RequestMapping("/api/v1/tours")
@RequiredArgsConstructor
public class TourController {

    private final TourService tourService;

    @GetMapping
    public ResponseEntity<TourStateDTO> get(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(tourService.get(email));
    }

    @PostMapping("/{tour}/seen")
    public ResponseEntity<Void> markSeen(@PathVariable TourKey tour, @AuthenticationPrincipal String email) {
        tourService.markSeen(tour, email);
        return ResponseEntity.noContent().build();
    }
}
