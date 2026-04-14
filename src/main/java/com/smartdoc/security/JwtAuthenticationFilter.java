package com.smartdoc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Intercepts every HTTP request exactly once (OncePerRequestFilter).
 *
 * What it does step by step:
 * 1. Injects a correlationId into MDC for log tracing
 * 2. Reads the Authorization header
 * 3. Extracts the JWT token (strips "Bearer " prefix)
 * 4. Validates the token
 * 5. If valid → puts user into Spring Security context
 * 6. Passes request to next filter/controller
 *
 * Interview point: What is SecurityContext?
 * It's a thread-local storage that holds the authenticated user
 * for the duration of the request. Controllers can access it
 * via SecurityContextHolder.getContext().getAuthentication()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: Inject correlation ID for distributed log tracing
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {
            // Step 2 & 3: Extract token from header
            String token = extractTokenFromHeader(request);

            // Step 4 & 5: Validate and set authentication
            if (token != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                String username = jwtUtils.getUsernameFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtils.validateToken(token, userDetails)) {

                    // Create authentication object
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,                           // credentials null after auth
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Put authenticated user into SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {}", username);
                }
            }

        } catch (Exception e) {
            // Don't throw — just log and let request continue
            // Spring Security will reject it as 401 if no auth is set
            log.warn("Could not authenticate request: {}", e.getMessage());

        } finally {
            // Step 6: Always continue the filter chain
            filterChain.doFilter(request, response);
            MDC.clear();    // clean up MDC after request completes
        }
    }

    /**
     * Extracts raw token from "Authorization: Bearer <token>" header.
     * Returns null if header is missing or malformed.
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);     // "Bearer " is 7 characters
        }
        return null;
    }
}