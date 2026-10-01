package com.auth.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import com.auth.auth.model.User;
import com.auth.auth.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Runs on every request: reads the token, checks it, and tells Spring who the user is
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Read by SecurityConfig to explain why a 401 happened
    public static final String JWT_ERROR_ATTRIBUTE = "jwt_error";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // No token: continue as a guest. Public endpoints work, protected ones return 401.
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtUtil.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);

            // A valid token must stop working once the account is deleted, disabled or locked
            if (user == null || !user.isEnabled() || user.isLocked()) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, "Account is disabled or locked");
            } else {
                // The role decides what this user may access (see SecurityConfig)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getUserType().name())));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, "Invalid or expired token");
        }

        filterChain.doFilter(request, response);
    }

    // Cookie first (browsers), then "Authorization: Bearer ..." (mobile apps, Postman)
    private String resolveToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, JwtUtil.ACCESS_TOKEN_COOKIE);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
