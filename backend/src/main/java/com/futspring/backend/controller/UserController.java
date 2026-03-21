package com.futspring.backend.controller;

import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.dto.UserResponseDTO;
import com.futspring.backend.service.PeladaService;
import com.futspring.backend.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final PeladaService peladaService;
    private final StatsService statsService;

    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDTO>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(peladaService.searchUsers(q));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<StatsDTO> getUserStats(@PathVariable Long id, Authentication authentication) {
        String callerEmail = (String) authentication.getPrincipal();
        return ResponseEntity.ok(statsService.getStats(id, callerEmail));
    }
}
