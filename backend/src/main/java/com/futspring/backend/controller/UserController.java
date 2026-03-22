package com.futspring.backend.controller;

import com.futspring.backend.dto.ProfileDTO;
import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.dto.UpdateProfileRequest;
import com.futspring.backend.dto.UserMatchHistoryDTO;
import com.futspring.backend.dto.UserResponseDTO;
import com.futspring.backend.dto.UserStatsTimelineDTO;
import com.futspring.backend.service.PeladaService;
import com.futspring.backend.service.StatsService;
import com.futspring.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<List<UserResponseDTO>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(peladaService.searchUsers(q));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<StatsDTO> getUserStats(@PathVariable Long id, Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(statsService.getStats(id, callerEmail));
    }

    @GetMapping("/{id}/stats/timeline")
    public ResponseEntity<UserStatsTimelineDTO> getUserStatsTimeline(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(statsService.getTimeline(id, from, to, callerEmail));
    }

    @GetMapping("/{id}/stats/matches")
    public ResponseEntity<UserMatchHistoryDTO> getUserMatchHistory(
            @PathVariable Long id,
            Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(statsService.getMatchHistory(id, callerEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileDTO> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileDTO> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(id, request, callerEmail));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ProfileDTO> uploadUserImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(userService.uploadUserImage(id, file, callerEmail));
    }

    @PostMapping("/{id}/background-image")
    public ResponseEntity<ProfileDTO> uploadBackgroundImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(userService.uploadBackgroundImage(id, file, callerEmail));
    }
}
