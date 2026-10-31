package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.chat.ChatService;
import com.futspring.backend.domain.chat.dto.MessageDTO;
import com.futspring.backend.domain.pelada.dto.AddPlayerRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkRemovePlayersRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkSetAdminRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkSetMensalistaRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkSetStarsRequestDTO;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.PeladaDetailResponseDTO;
import com.futspring.backend.domain.pelada.dto.PeladaMemberDTO;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.dto.SetAdminRequestDTO;
import com.futspring.backend.domain.pelada.dto.SetMensalistaRequestDTO;
import com.futspring.backend.domain.pelada.dto.TransferCreatorRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePlayerStarsRequestDTO;
import com.futspring.backend.domain.stats.AttendanceService;
import com.futspring.backend.domain.stats.AwardsService;
import com.futspring.backend.domain.stats.RankingService;
import com.futspring.backend.domain.stats.dto.PeladaAttendanceDTO;
import com.futspring.backend.domain.stats.dto.PeladaAwardsDTO;
import com.futspring.backend.domain.stats.dto.PlayerPeladaHistoryDTO;
import com.futspring.backend.domain.stats.dto.PlayerPeladaStatsDTO;
import com.futspring.backend.domain.stats.dto.RankingDTO;
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
    private final AttendanceService attendanceService;

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
        peladaService.addPlayer(id, request.userId(), email);
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

    // Bulk member actions: atomic, every target must be a member of this pelada
    @PutMapping("/{id}/players/admin")
    public ResponseEntity<List<PeladaMemberDTO>> setAdmins(
            @PathVariable Long id,
            @Valid @RequestBody BulkSetAdminRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.setAdmins(id, request.userIds(), request.isAdmin(), email));
    }

    @PutMapping("/{id}/players/mensalista")
    public ResponseEntity<List<PeladaMemberDTO>> setMensalistas(
            @PathVariable Long id,
            @Valid @RequestBody BulkSetMensalistaRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.setMensalistas(id, request.userIds(), request.isMensalista(), email));
    }

    @PutMapping("/{id}/players/stars")
    public ResponseEntity<List<PeladaMemberDTO>> setPlayersStars(
            @PathVariable Long id,
            @Valid @RequestBody BulkSetStarsRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.setPlayersStars(id, request.userIds(), request.stars(), email));
    }

    // POST instead of DELETE with a body, which proxies and axios handle poorly
    @PostMapping("/{id}/players/remove")
    public ResponseEntity<Void> removePlayers(
            @PathVariable Long id,
            @Valid @RequestBody BulkRemovePlayersRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        peladaService.removePlayers(id, request.userIds(), email);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/players/{userId}/admin")
    public ResponseEntity<Void> setAdmin(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody SetAdminRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        peladaService.setAdmin(id, userId, request.isAdmin(), email);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/players/{userId}/mensalista")
    public ResponseEntity<PeladaMemberDTO> setMensalista(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody SetMensalistaRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.setMensalista(id, userId, request.isMensalista(), email));
    }

    @PutMapping("/{id}/players/{userId}/stars")
    public ResponseEntity<PeladaMemberDTO> setPlayerStars(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody UpdatePlayerStarsRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.setPlayerStars(id, userId, request.stars(), email));
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

    @PutMapping("/{id}/creator")
    public ResponseEntity<PeladaResponseDTO> transferCreator(
            @PathVariable Long id,
            @Valid @RequestBody TransferCreatorRequestDTO request,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.transferCreator(id, request.userId(), email));
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

    @GetMapping("/{id}/attendance")
    public ResponseEntity<PeladaAttendanceDTO> getAttendanceRanking(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(attendanceService.getAttendanceRanking(id, email));
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

    @DeleteMapping("/{id}/image")
    public ResponseEntity<PeladaResponseDTO> removeImage(
            @PathVariable Long id,
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(peladaService.removePeladaImage(id, email));
    }
}
