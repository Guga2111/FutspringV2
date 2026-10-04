package com.futspring.backend.controller;

import com.futspring.backend.dto.AuthResponseDTO;
import com.futspring.backend.dto.LoginRequestDTO;
import com.futspring.backend.dto.RegisterRequestDTO;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.service.AuthService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket resolveBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest
    ) {
        String ip = httpRequest.getRemoteAddr();
        Bucket bucket = resolveBucket(ip);
        if (!bucket.tryConsume(1)) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please try again later.");
        }
        return ResponseEntity.ok(authService.login(request));
    }
}
