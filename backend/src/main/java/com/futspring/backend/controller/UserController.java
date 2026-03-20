package com.futspring.backend.controller;

import com.futspring.backend.dto.UserResponseDTO;
import com.futspring.backend.service.PeladaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final PeladaService peladaService;

    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDTO>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(peladaService.searchUsers(q));
    }
}
