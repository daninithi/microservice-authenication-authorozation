package com.auth.auth.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth.auth.dto.LoginRequest;
import com.auth.auth.dto.RefreshTokenRequest;
import com.auth.auth.dto.RegisterRequest;
import com.auth.auth.dto.ResponseMessage;
import com.auth.auth.security.JwtUtil;
import com.auth.auth.service.AuthService;
import com.auth.auth.shared.ResponseStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // The refresh cookie is only sent to these endpoints, not to every API call
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    // Secure cookies are only sent over HTTPS (browsers also allow them on localhost)
    @Value("${jwt.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseMessage<>(ResponseStatus.Success, "User registered successfully"));
    }

    // Tokens go into HttpOnly cookies (browsers) and the body (Bearer clients)
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // getRemoteAddr() can't be faked by the client (unlike the X-Forwarded-For header)
        Map<String, Object> tokens = authService.login(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .headers(tokenCookies(tokens))
                .body(new ResponseMessage<>(ResponseStatus.Success, tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = JwtUtil.REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest request) {

        Map<String, Object> tokens = authService.refresh(resolveRefreshToken(cookieToken, request));
        return ResponseEntity.ok()
                .headers(tokenCookies(tokens))
                .body(new ResponseMessage<>(ResponseStatus.Success, tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = JwtUtil.REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest request) {

        authService.logout(resolveRefreshToken(cookieToken, request));

        // Always clear the cookies, even if the token was already gone
        return ResponseEntity.ok()
                .headers(clearCookies())
                .body(new ResponseMessage<>(ResponseStatus.Success, "Logged out successfully"));
    }

    // ---------- cookie helpers ----------

    // Cookie first, request body as a fallback
    private String resolveRefreshToken(String cookieToken, RefreshTokenRequest request) {
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }
        return request != null ? request.getRefreshToken() : null;
    }

    private HttpHeaders tokenCookies(Map<String, Object> tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(JwtUtil.ACCESS_TOKEN_COOKIE,
                (String) tokens.get("accessToken"), "/",
                Duration.ofMillis(jwtUtil.getAccessTokenExpiration())));
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(JwtUtil.REFRESH_TOKEN_COOKIE,
                (String) tokens.get("refreshToken"), REFRESH_COOKIE_PATH,
                AuthService.REFRESH_TOKEN_DURATION));
        return headers;
    }

    // Max-Age=0 tells the browser to delete the cookie
    private HttpHeaders clearCookies() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(JwtUtil.ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO));
        headers.add(HttpHeaders.SET_COOKIE, buildCookie(JwtUtil.REFRESH_TOKEN_COOKIE, "", REFRESH_COOKIE_PATH, Duration.ZERO));
        return headers;
    }

    // HttpOnly: JavaScript can't read it. SameSite=Strict: not sent from other websites.
    private String buildCookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
