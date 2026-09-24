package com.p.logistics.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Step 06 security foundation, item 1 (service identity): inventory-service
// is meant to be reached only by order-service, never directly
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the business API is the internal-only boundary. The actuator
        // health check stays open - it's an infrastructure signal (the
        // Compose healthcheck calls it directly), not a business operation,
        // and it has no reason to know this key.
        return !request.getRequestURI().startsWith("/inventory");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (!expectedApiKey.equals(providedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid " + API_KEY_HEADER);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
