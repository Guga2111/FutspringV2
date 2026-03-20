package com.futspring.backend.controller;

import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.PeladaDetailResponseDTO;
import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.service.PeladaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}
