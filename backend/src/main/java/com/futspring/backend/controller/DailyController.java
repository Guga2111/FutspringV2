package com.futspring.backend.controller;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.dto.FinalizeDailyRequestDTO;
import com.futspring.backend.dto.MatchResultDTO;
import com.futspring.backend.dto.SwapPlayersRequestDTO;
import com.futspring.backend.dto.UpdateDailyStatusRequestDTO;
import com.futspring.backend.service.DailyService;
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
        return ResponseEntity.ok(dailyService.confirmAttendance(id, email));
    }

    @DeleteMapping("/dailies/{id}/confirm")
    public ResponseEntity<DailyListItemDTO> disconfirmAttendance(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.disconfirmAttendance(id, email));
    }

    @PostMapping("/dailies/{id}/sort-teams")
    public ResponseEntity<java.util.List<DailyDetailDTO.TeamDTO>> sortTeams(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.sortTeams(id, email));
    }

    @PutMapping("/dailies/{id}/teams/swap")
    public ResponseEntity<java.util.List<DailyDetailDTO.TeamDTO>> swapPlayers(
            @PathVariable Long id,
            @RequestBody SwapPlayersRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.swapPlayers(id, request.getPlayer1Id(), request.getPlayer2Id(), email));
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
    public ResponseEntity<java.util.List<DailyDetailDTO.MatchDTO>> submitResults(
            @PathVariable Long id,
            @RequestBody java.util.List<MatchResultDTO> results,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.submitResults(id, results, email));
    }

    @PostMapping("/dailies/{id}/finalize")
    public ResponseEntity<DailyDetailDTO> finalizeDaily(
            @PathVariable Long id,
            @RequestBody FinalizeDailyRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.finalizeDaily(id, request.getPuskasWinnerId(), request.getWiltballWinnerId(), email));
    }

    @PutMapping("/dailies/{id}/champion-image")
    public ResponseEntity<DailyListItemDTO> uploadChampionImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(dailyService.uploadChampionImage(id, file, email));
    }
}
