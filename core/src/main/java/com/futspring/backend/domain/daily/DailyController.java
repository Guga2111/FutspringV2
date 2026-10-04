package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.CreateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.dto.FinalizeDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.SwapPlayersRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateDailyStatusRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateTeamColorRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateTeamNameRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// @Validated enables validation of the List<@Valid MatchResultDTO> body (ConstraintViolationException → 400)
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DailyController {

    private final DailyService dailyService;
    private final DailyAttendanceService dailyAttendanceService;
    private final DailyTeamManagementService dailyTeamManagementService;
    private final DailyResultsService dailyResultsService;

    @PostMapping("/peladas/{peladaId}/dailies")
    public ResponseEntity<DailyListItemDTO> createDaily(@PathVariable Long peladaId,
                                                        @Valid @RequestBody CreateDailyRequestDTO request,
                                                        @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dailyService.createDaily(peladaId, request, email));
    }

    @GetMapping("/peladas/{peladaId}/dailies")
    public ResponseEntity<List<DailyListItemDTO>> getDailiesForPelada(@PathVariable Long peladaId,
                                                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyService.getDailiesForPelada(peladaId, email));
    }

    @GetMapping("/dailies/{id}")
    public ResponseEntity<DailyDetailDTO> getDailyDetail(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyService.getDailyDetail(id, email));
    }

    @PostMapping("/dailies/{id}/confirm")
    public ResponseEntity<DailyListItemDTO> confirmAttendance(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyAttendanceService.confirmAttendance(id, email));
    }

    @DeleteMapping("/dailies/{id}/confirm")
    public ResponseEntity<DailyListItemDTO> disconfirmAttendance(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyAttendanceService.disconfirmAttendance(id, email));
    }

    @PostMapping("/dailies/{id}/confirm/{userId}")
    public ResponseEntity<DailyListItemDTO> adminConfirmAttendance(@PathVariable Long id, @PathVariable Long userId,
                                                                   @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyAttendanceService.adminConfirmAttendance(id, userId, email));
    }

    @DeleteMapping("/dailies/{id}/confirm/{userId}")
    public ResponseEntity<DailyListItemDTO> adminDisconfirmAttendance(@PathVariable Long id, @PathVariable Long userId,
                                                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyAttendanceService.adminDisconfirmAttendance(id, userId, email));
    }

    @PostMapping("/dailies/{id}/sort-teams")
    public ResponseEntity<List<DailyDetailDTO.TeamDTO>> sortTeams(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyTeamManagementService.sortTeams(id, email));
    }

    @PutMapping("/dailies/{id}/teams/swap")
    public ResponseEntity<List<DailyDetailDTO.TeamDTO>> swapPlayers(@PathVariable Long id,
                                                                    @Valid @RequestBody SwapPlayersRequestDTO request,
                                                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyTeamManagementService.swapPlayers(id, request.getPlayer1Id(), request.getPlayer2Id(), email));
    }

    @PatchMapping("/dailies/{dailyId}/teams/{teamId}/name")
    public ResponseEntity<DailyDetailDTO.TeamDTO> updateTeamName(@PathVariable Long dailyId, @PathVariable Long teamId,
                                                                 @Valid @RequestBody UpdateTeamNameRequestDTO request,
                                                                 @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyTeamManagementService.updateTeamName(dailyId, teamId, request.getName(), email));
    }

    @PatchMapping("/dailies/{dailyId}/teams/{teamId}/color")
    public ResponseEntity<DailyDetailDTO.TeamDTO> updateTeamColor(@PathVariable Long dailyId, @PathVariable Long teamId,
                                                                  @Valid @RequestBody UpdateTeamColorRequestDTO request,
                                                                  @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyTeamManagementService.updateTeamColor(dailyId, teamId, request.getColor(), email));
    }

    @PutMapping("/dailies/{id}/status")
    public ResponseEntity<DailyListItemDTO> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateDailyStatusRequestDTO request,
                                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyService.updateStatus(id, request.getStatus(), email));
    }

    @PostMapping("/dailies/{id}/results")
    public ResponseEntity<List<DailyDetailDTO.MatchDTO>> submitResults(
            @PathVariable Long id,
            @RequestBody @NotEmpty(message = "Informe pelo menos uma partida") List<@Valid MatchResultDTO> results,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyResultsService.submitResults(id, results, email));
    }

    // Returns the updated detail: the service call that changes data runs first, in its own transaction
    @PostMapping("/dailies/{id}/finalize")
    public ResponseEntity<DailyDetailDTO> finalizeDaily(@PathVariable Long id,
                                                        @Valid @RequestBody FinalizeDailyRequestDTO request,
                                                        @AuthenticationPrincipal String email) {
        dailyResultsService.finalizeDaily(id, request.getPuskasWinnerIds(), request.getWiltballWinnerIds(), email);
        return ResponseEntity.ok(dailyService.getDailyDetail(id, email));
    }

    @PostMapping("/dailies/{id}/populate")
    public ResponseEntity<DailyDetailDTO> populateFromMessage(@PathVariable Long id,
                                                              @Valid @RequestBody PopulateDailyRequestDTO request,
                                                              @AuthenticationPrincipal String email) {
        dailyResultsService.populateFromMessage(id, request, email);
        return ResponseEntity.ok(dailyService.getDailyDetail(id, email));
    }

    @PutMapping("/dailies/{id}/champion-image")
    public ResponseEntity<DailyListItemDTO> uploadChampionImage(@PathVariable Long id,
                                                                @RequestParam("file") MultipartFile file,
                                                                @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dailyResultsService.uploadChampionImage(id, file, email));
    }

    @DeleteMapping("/dailies/{id}")
    public ResponseEntity<Void> deleteDaily(@PathVariable Long id, @AuthenticationPrincipal String email) {
        dailyService.deleteDaily(id, email);
        return ResponseEntity.noContent().build();
    }
}
