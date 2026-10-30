package com.futspring.backend.domain.auth;

import com.futspring.backend.shared.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    // User.tokenVersion at issue time; AccessTokenVerifier rejects the token once it changes
    static final String VERSION_CLAIM = "ver";

    private final JwtConfig jwtConfig;
    private final SecretKey key;

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email, int tokenVersion) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("email", email)
                .claim(VERSION_CLAIM, tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpirationMs()))
                // explicit: JJWT would pick HS384/HS512 for a secret of 48+/64+ bytes
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Signature and expiry only; the session check (token version) is in AccessTokenVerifier
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(extractAllClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
