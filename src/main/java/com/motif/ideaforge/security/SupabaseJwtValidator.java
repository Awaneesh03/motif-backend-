package com.motif.ideaforge.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Validator for Supabase JWT tokens
 */
@Component
@Slf4j
public class SupabaseJwtValidator {

    private final SecretKey secretKey;

    public SupabaseJwtValidator(@Value("${app.supabase.jwt-secret}") String jwtSecret) {
        // Log secret loading status (never log the actual secret!)
        log.info("=== SUPABASE JWT SECRET LOADING ===");
        log.info("JWT Secret provided: {}", jwtSecret != null && !jwtSecret.isEmpty());
        log.info("JWT Secret length: {} characters", jwtSecret != null ? jwtSecret.length() : 0);
        
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            log.error("CRITICAL: SUPABASE_JWT_SECRET is not set! Check your .env file.");
            throw new IllegalStateException("SUPABASE_JWT_SECRET environment variable is required but not set");
        }
        
        // Try both approaches - Supabase can work either way depending on configuration
        // Approach 1: Use secret as raw UTF-8 bytes (most common for Supabase)
        byte[] keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        
        log.info("Using secret as raw UTF-8 bytes: {} bytes", keyBytes.length);
        log.info("Secret key algorithm: {}", secretKey.getAlgorithm());
        log.info("=== JWT SECRET LOADED SUCCESSFULLY ===");
    }

    public Claims validateToken(String token) {
        try {
            // Debug: Parse JWT header to see claimed algorithm
            String[] parts = token.split("\\.");
            if (parts.length >= 1) {
                String header = new String(Base64.getUrlDecoder().decode(parts[0]));
                log.info("JWT Header: {}", header);
            }
            
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
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
