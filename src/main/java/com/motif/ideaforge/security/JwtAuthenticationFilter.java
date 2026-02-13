package com.motif.ideaforge.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that validates Supabase tokens
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SupabaseJwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        
        log.info("=== JWT FILTER: {} {} ===", method, requestPath);
        log.info("Authorization header present: {}", authHeader != null);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.info("No Bearer token - proceeding without authentication");
            filterChain.doFilter(request, response);
            return;
        }

        log.info("Bearer token found, length: {} chars", authHeader.length() - 7);

        try {
            String jwt = authHeader.substring(7);
            log.info("Attempting to validate JWT...");
            
            Claims claims = jwtValidator.validateToken(jwt);

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            
            log.info("JWT VALID - User ID: {}, Email: {}", userId, email);

            UserPrincipal principal = new UserPrincipal(userId, email);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("SecurityContext set - User authenticated successfully");

        } catch (Exception e) {
            log.error("JWT validation FAILED: {}", e.getMessage());
            log.error("Authentication will be cleared - request will be denied");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/api-docs/") ||
                path.startsWith("/v3/api-docs/");
    }
}
