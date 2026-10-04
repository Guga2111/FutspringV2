package com.futspring.backend.controller;

import com.futspring.backend.dto.AddPlayerRequestDTO;
import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.MessageDTO;
import com.futspring.backend.dto.PeladaAwardsDTO;
import com.futspring.backend.dto.PeladaDetailResponseDTO;
import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.dto.PlayerPeladaHistoryDTO;
import com.futspring.backend.dto.PlayerPeladaStatsDTO;
import com.futspring.backend.dto.RankingDTO;
import com.futspring.backend.dto.SetAdminRequestDTO;
import com.futspring.backend.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.service.AwardsService;
import com.futspring.backend.service.ChatService;
import com.futspring.backend.service.PeladaService;
import com.futspring.backend.service.RankingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/peladas")
@RequiredArgsConstructor
public class PeladaController {

    private final PeladaService peladaService;
    private final RankingService rankingService;
    private final ChatService chatService;
    private final AwardsService awardsService;

    @PostMapping
    public ResponseEntity<PeladaResponseDTO> createPelada(
            @Valid @RequestBody CreatePeladaRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(peladaService.createPelada(request, email));
    }

    @GetMapping("/my")
    public ResponseEntity<List<PeladaResponseDTO>> getMyPeladas(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(peladaService.getMyPeladas(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PeladaDetailResponseDTO> getPeladaDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.getPeladaDetail(id, email));
    }

    @PostMapping("/{id}/players")
    public ResponseEntity<Void> addPlayer(
            @PathVariable Long id,
            @Valid @RequestBody AddPlayerRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        peladaService.addPlayer(id, request.getUserId(), email);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/players/{userId}")
    public ResponseEntity<Void> removePlayer(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal String email
    ) {
        peladaService.removePlayer(id, userId, email);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/players/{userId}/admin")
    public ResponseEntity<Void> setAdmin(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody SetAdminRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        peladaService.setAdmin(id, userId, request.getIsAdmin(), email);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<PeladaResponseDTO> updatePelada(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePeladaRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.updatePelada(id, request, email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePelada(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        peladaService.deletePelada(id, email);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/ranking")
    public ResponseEntity<List<RankingDTO>> getRanking(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(rankingService.getRanking(id, email));
    }

    @GetMapping("/{id}/members/{userId}/stats")
    public ResponseEntity<PlayerPeladaStatsDTO> getPlayerPeladaStats(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(rankingService.getPlayerPeladaStats(id, userId, email));
    }

    @GetMapping("/{id}/members/{userId}/history")
    public ResponseEntity<PlayerPeladaHistoryDTO> getPlayerPeladaHistory(
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(rankingService.getPlayerPeladaHistory(id, userId, limit, email));
    }

    @GetMapping("/{id}/awards")
    public ResponseEntity<PeladaAwardsDTO> getAwards(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(awardsService.getAwards(id, email));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDTO>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(chatService.getHistory(id, email, page, size));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<PeladaResponseDTO> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.uploadPeladaImage(id, file, email));
    }
}
