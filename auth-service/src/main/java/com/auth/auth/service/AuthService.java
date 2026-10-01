package com.auth.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.auth.auth.dto.LoginRequest;
import com.auth.auth.dto.RegisterRequest;
import com.auth.auth.model.RefreshToken;
import com.auth.auth.model.User;
import com.auth.auth.repository.RefreshTokenRepository;
import com.auth.auth.repository.UserRepository;
import com.auth.auth.security.JwtUtil;
import com.auth.auth.shared.UserType;

@Service
public class AuthService {

    public static final Duration REFRESH_TOKEN_DURATION = Duration.ofDays(7);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // Per-IP limit, so one attacker can't lock many accounts or guess passwords quickly
    private static final int MAX_LOGIN_REQUESTS_PER_IP = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final SecureRandom secureRandom = new SecureRandom();

    // IP address -> login attempts in the current 1-minute window (in memory, single server)
    private final Map<String, LoginWindow> loginAttemptsByIp = new ConcurrentHashMap<>();

    private record LoginWindow(Instant start, int count) {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // ---------- register ----------

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = request.getUsername().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.REGULAR_USER);

        userRepository.save(user);
        logger.info("New user registered: {}", email);
    }

    // ---------- login (authentication) ----------

    // Not @Transactional on purpose: a failed login must still save the failed-attempt counter
    public Map<String, Object> login(LoginRequest request, String ipAddress) {
        checkLoginRateLimit(ipAddress);

        String email = normalizeEmail(request.getEmail());

        // Same message for "no such user" and "wrong password", so attackers can't guess emails
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        if (user.isLocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account is temporarily locked due to too many failed login attempts");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailedLogin(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Successful login resets the counter
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        return issueTokens(user);
    }

    // ---------- refresh ----------

    // noRollbackFor: keep the "revoke all sessions" below even though we then throw
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        // A revoked token used again was probably stolen: log the user out everywhere
        if (refreshToken.isRevoked()) {
            logger.warn("Refresh token reuse detected for user {}; revoking all sessions",
                    refreshToken.getUser().getId());
            refreshTokenRepository.revokeAllByUser(refreshToken.getUser());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        User user = refreshToken.getUser();
        if (!user.isEnabled() || user.isLocked()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled or locked");
        }

        // Rotation: each refresh token works only once
        refreshToken.setRevoked(true);

        return issueTokens(user);
    }

    // ---------- logout ----------

    // Safe to call many times: an unknown or already-revoked token is ignored
    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevoked(true));
    }

    // ---------- scheduled cleanup ----------

    // Every night at 03:00: remove expired refresh tokens and old rate-limit entries
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanUp() {
        int deleted = refreshTokenRepository.deleteAllExpired(Instant.now());
        loginAttemptsByIp.values().removeIf(window -> isWindowOver(window));
        logger.info("Cleanup: deleted {} expired refresh tokens", deleted);
    }

    // ---------- helpers ----------

    private void checkLoginRateLimit(String ipAddress) {
        LoginWindow window = loginAttemptsByIp.compute(ipAddress, (ip, current) ->
                current == null || isWindowOver(current)
                        ? new LoginWindow(Instant.now(), 1)
                        : new LoginWindow(current.start(), current.count() + 1));

        if (window.count() > MAX_LOGIN_REQUESTS_PER_IP) {
            logger.warn("Too many login requests from IP {}", ipAddress);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again in a minute");
        }
    }

    private boolean isWindowOver(LoginWindow window) {
        return window.start().plus(RATE_LIMIT_WINDOW).isBefore(Instant.now());
    }

    private Map<String, Object> issueTokens(User user) {
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("accessToken", jwtUtil.generateToken(user));
        tokens.put("refreshToken", createRefreshToken(user));
        tokens.put("tokenType", "Bearer");
        tokens.put("expiresIn", jwtUtil.getAccessTokenExpiration() / 1000);
        return tokens;
    }

    // Random 32-byte token; only its hash is stored in the database
    private String createRefreshToken(User user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_DURATION));
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    private void recordFailedLogin(User user) {
        // An old lock has expired: start counting again from zero
        if (user.getLockedUntil() != null && !user.isLocked()) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            logger.warn("Account {} locked after {} failed logins", user.getEmail(), user.getFailedLoginAttempts());
        }

        userRepository.save(user);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
