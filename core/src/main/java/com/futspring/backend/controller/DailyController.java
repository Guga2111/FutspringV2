package com.futspring.backend.controller;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.dto.FinalizeDailyRequestDTO;
import com.futspring.backend.dto.MatchResultDTO;
import com.futspring.backend.dto.PopulateDailyRequestDTO;
import com.futspring.backend.dto.SwapPlayersRequestDTO;
import com.futspring.backend.dto.UpdateDailyStatusRequestDTO;
import com.futspring.backend.dto.UpdateTeamNameRequestDTO;
import com.futspring.backend.dto.UpdateTeamColorRequestDTO;
import com.futspring.backend.service.DailyAttendanceService;
import com.futspring.backend.service.DailyResultsService;
import com.futspring.backend.service.DailyService;
import com.futspring.backend.service.DailyTeamManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DailyController {

    private final DailyService dailyService;
    private final DailyAttendanceService dailyAttendanceService;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyResultsService dailyResultsService;

    @PostMapping("/peladas/{peladaId}/dailies")
    public ResponseEntity<DailyListItemDTO> createDaily(
            @PathVariable Long peladaId,
            @Valid @RequestBody CreateDailyRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(dailyService.createDaily(peladaId, request, email));
    }

    @GetMapping("/peladas/{peladaId}/dailies")
    public ResponseEntity<List<DailyListItemDTO>> getDailiesForPelada(
            @PathVariable Long peladaId,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.getDailiesForPelada(peladaId, email));
    }

    @GetMapping("/dailies/{id}")
    public ResponseEntity<DailyDetailDTO> getDailyDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.getDailyDetail(id, email));
    }

    @PostMapping("/dailies/{id}/confirm")
    public ResponseEntity<DailyListItemDTO> confirmAttendance(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyAttendanceService.confirmAttendance(id, email));
    }

    @DeleteMapping("/dailies/{id}/confirm")
    public ResponseEntity<DailyListItemDTO> disconfirmAttendance(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyAttendanceService.disconfirmAttendance(id, email));
    }

    @PostMapping("/dailies/{id}/sort-teams")
    public ResponseEntity<List<DailyDetailDTO.TeamDTO>> sortTeams(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyTeamManagementService.sortTeams(id, email));
    }

    @PutMapping("/dailies/{id}/teams/swap")
    public ResponseEntity<List<DailyDetailDTO.TeamDTO>> swapPlayers(
            @PathVariable Long id,
            @RequestBody SwapPlayersRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyTeamManagementService.swapPlayers(id, request.getPlayer1Id(), request.getPlayer2Id(), email));
    }

    @PutMapping("/dailies/{id}/status")
    public ResponseEntity<DailyListItemDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateDailyStatusRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.updateStatus(id, request.getStatus(), email));
    }

    @PostMapping("/dailies/{id}/results")
    public ResponseEntity<List<DailyDetailDTO.MatchDTO>> submitResults(
            @PathVariable Long id,
            @RequestBody List<MatchResultDTO> results,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyResultsService.submitResults(id, results, email));
    }

    @PostMapping("/dailies/{id}/finalize")
    public ResponseEntity<DailyDetailDTO> finalizeDaily(
            @PathVariable Long id,
            @RequestBody FinalizeDailyRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        dailyResultsService.finalizeDaily(id, request.getPuskasWinnerIds(), request.getWiltballWinnerIds(), email);
        return ResponseEntity.ok(dailyService.getDailyDetail(id, email));
    }

    @PostMapping("/dailies/{id}/confirm/{userId}")
    public ResponseEntity<DailyListItemDTO> adminConfirmAttendance(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyAttendanceService.adminConfirmAttendance(id, userId, email));
    }

    @DeleteMapping("/dailies/{id}/confirm/{userId}")
    public ResponseEntity<DailyListItemDTO> adminDisconfirmAttendance(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyAttendanceService.adminDisconfirmAttendance(id, userId, email));
    }

    @PatchMapping("/dailies/{dailyId}/teams/{teamId}/name")
    public ResponseEntity<DailyDetailDTO.TeamDTO> updateTeamName(
            @PathVariable Long dailyId,
            @PathVariable Long teamId,
            @RequestBody UpdateTeamNameRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyTeamManagementService.updateTeamName(dailyId, teamId, request.getName(), email));
    }

    @PatchMapping("/dailies/{dailyId}/teams/{teamId}/color")
    public ResponseEntity<DailyDetailDTO.TeamDTO> updateTeamColor(
            @PathVariable Long dailyId,
            @PathVariable Long teamId,
            @RequestBody UpdateTeamColorRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyTeamManagementService.updateTeamColor(dailyId, teamId, request.getColor(), email));
    }

    @PostMapping("/dailies/{id}/populate")
    public ResponseEntity<DailyDetailDTO> populateFromMessage(
            @PathVariable Long id,
            @RequestBody PopulateDailyRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        dailyResultsService.populateFromMessage(id, request, email);
        return ResponseEntity.ok(dailyService.getDailyDetail(id, email));
    }

    @DeleteMapping("/dailies/{id}")
    public ResponseEntity<Void> deleteDaily(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        dailyService.deleteDaily(id, email);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/dailies/{id}/champion-image")
    public ResponseEntity<DailyListItemDTO> uploadChampionImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyResultsService.uploadChampionImage(id, file, email));
    }
}
