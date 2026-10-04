package com.futspring.backend.domain.user;

import com.futspring.backend.domain.pelada.PeladaService;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.stats.StatsService;
import com.futspring.backend.domain.stats.dto.StatsDTO;
import com.futspring.backend.domain.stats.dto.UserMatchHistoryDTO;
import com.futspring.backend.domain.stats.dto.UserStatsTimelineDTO;
import com.futspring.backend.domain.user.dto.ProfileDTO;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final PeladaService peladaService;
    private final StatsService statsService;
    private final UserService userService;

    @GetMapping("/search")
    public ResponseEntity<List<PublicUserDTO>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(peladaService.searchUsers(q));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<StatsDTO> getUserStats(@PathVariable Long id, @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(statsService.getStats(id, callerEmail));
    }

    @GetMapping("/{id}/stats/timeline")
    public ResponseEntity<UserStatsTimelineDTO> getUserStatsTimeline(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(statsService.getTimeline(id, from, to, callerEmail));
    }

    @GetMapping("/{id}/stats/matches")
    public ResponseEntity<UserMatchHistoryDTO> getUserMatchHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(statsService.getMatchHistory(id, callerEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileDTO> getProfile(@PathVariable Long id, @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(userService.getProfile(id, callerEmail));
    }

    // Peladas that the caller and this user both belong to
    @GetMapping("/{id}/peladas")
    public ResponseEntity<List<PeladaResponseDTO>> getSharedPeladas(@PathVariable Long id,
                                                                    @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(peladaService.getSharedPeladas(id, callerEmail));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileDTO> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(userService.updateProfile(id, request, callerEmail));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ProfileDTO> uploadUserImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(userService.uploadUserImage(id, file, callerEmail));
    }

    @PostMapping("/{id}/background-image")
    public ResponseEntity<ProfileDTO> uploadBackgroundImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String callerEmail) {
        return ResponseEntity.ok(userService.uploadBackgroundImage(id, file, callerEmail));
    }
}
