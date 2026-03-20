package com.futspring.backend.controller;

import com.futspring.backend.dto.AddPlayerRequestDTO;
import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.PeladaDetailResponseDTO;
import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.dto.SetAdminRequestDTO;
import com.futspring.backend.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.service.PeladaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/peladas")
@RequiredArgsConstructor
public class PeladaController {

    private final PeladaService peladaService;

    @PostMapping
    public ResponseEntity<PeladaResponseDTO> createPelada(
            @Valid @RequestBody CreatePeladaRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(peladaService.createPelada(request, email));
    }

    @GetMapping("/my")
    public ResponseEntity<List<PeladaResponseDTO>> getMyPeladas(Authentication authentication) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(peladaService.getMyPeladas(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PeladaDetailResponseDTO> getPeladaDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(peladaService.getPeladaDetail(id, email));
    }

    @PostMapping("/{id}/players")
    public ResponseEntity<Void> addPlayer(
            @PathVariable Long id,
            @Valid @RequestBody AddPlayerRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        peladaService.addPlayer(id, request.getUserId(), email);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/players/{userId}")
    public ResponseEntity<Void> removePlayer(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        peladaService.removePlayer(id, userId, email);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/players/{userId}/admin")
    public ResponseEntity<Void> setAdmin(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody SetAdminRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        peladaService.setAdmin(id, userId, request.getIsAdmin(), email);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<PeladaResponseDTO> updatePelada(
            @PathVariable Long id,
            @RequestBody UpdatePeladaRequestDTO request,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(peladaService.updatePelada(id, request, email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePelada(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        peladaService.deletePelada(id, email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<PeladaResponseDTO> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(peladaService.uploadPeladaImage(id, file, email));
    }
}
