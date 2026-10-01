package com.gateway.gateway.security;

import java.nio.charset.StandardCharsets;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import reactor.core.publisher.Mono;

// Authentication for the whole system happens here, once, for every request:
//  1. Remove any X-User-* headers the client sent (nobody can fake being an admin)
//  2. Public path?        -> let it through
//  3. No or bad token?    -> 401, the request never reaches a service
//  4. Valid token         -> add X-User-Id, X-User-Email, X-User-Role and forward
// The services trust these headers and use them for authorization (roles, "is this order yours?").
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtUtil jwtUtil;

    public AuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();

        String path = request.getURI().getRawPath();

        // "/api/v1/auth/../orders" could trick a simple path check, so refuse it
        if (path.contains("..") || path.contains("%2e") || path.contains("%2E")) {
            return writeError(exchange.getResponse(), HttpStatus.BAD_REQUEST, "Bad Request", "Invalid path");
        }

        if (isPublic(request.getMethod(), path)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        String token = resolveToken(request);
        if (token == null) {
            return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Authentication is required");
        }

        Claims claims;
        try {
            claims = jwtUtil.extractClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid or expired token");
        }

        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        if (userId == null || role == null) {
            return writeError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid or expired token");
        }

        ServerHttpRequest authenticatedRequest = request.mutate()
                .header(USER_ID_HEADER, userId)
                .header(USER_EMAIL_HEADER, claims.getSubject())
                .header(USER_ROLE_HEADER, role)
                .build();

        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    // Paths anyone can call without logging in
    private boolean isPublic(HttpMethod method, String path) {
        // Browser CORS check (answered by CorsGlobalConfig)
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        // Register, login, refresh, logout
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        // Browsing the shop
        return HttpMethod.GET.equals(method)
                && (path.equals("/api/v1/products") || path.startsWith("/api/v1/products/"));
    }

    // Cookie first (browsers), then "Authorization: Bearer ..." (mobile apps, Postman)
    private String resolveToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(JwtUtil.ACCESS_TOKEN_COOKIE);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    // Same JSON shape as ApiErrorResponse in product-service and order-service
    private Mono<Void> writeError(ServerHttpResponse response, HttpStatus status, String error, String message) {
        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + error
                + "\",\"messages\":[\"" + message + "\"]}";

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    // Run before the routing filters
    @Override
    public int getOrder() {
        return -1;
    }
}
