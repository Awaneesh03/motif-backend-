package com.motif.ideaforge.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validator for Supabase JWT tokens
 */
@Component
@Slf4j
public class SupabaseJwtValidator {

    private final SecretKey secretKey;

    public SupabaseJwtValidator(@Value("${app.supabase.jwt-secret}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new IllegalStateException("SUPABASE_JWT_SECRET environment variable is required but not set");
        }

        byte[] keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        log.info("JWT secret loaded successfully ({} bytes)", keyBytes.length);
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public String getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getSubject();
    }

    public String getEmailFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("email", String.class);
    }
}
