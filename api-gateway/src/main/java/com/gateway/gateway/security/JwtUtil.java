package com.gateway.gateway.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

// Reads JWT access tokens created by auth-service (same JWT_SECRET).
// The gateway only checks tokens; it never creates them.
@Component
public class JwtUtil {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey;

    // Runs once at startup, so a missing or weak secret stops the app immediately
    @PostConstruct
    public void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) must be at least 32 bytes long");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Throws JwtException if the signature is wrong or the token has expired
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
