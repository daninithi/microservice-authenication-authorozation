package com.auth.auth.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.auth.auth.model.User;
import com.auth.auth.repository.RefreshTokenRepository;
import com.auth.auth.repository.UserRepository;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserDetails)
                .toList();
    }

    @Transactional
    public void deleteUser(UUID userId, User currentAdmin) {
        if (userId.equals(currentAdmin.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Refresh tokens point to the user, so they must be deleted first
        refreshTokenRepository.deleteAllByUser(user);
        userRepository.delete(user);

        logger.info("User {} deleted by admin {}", user.getEmail(), currentAdmin.getEmail());
    }

    // Only safe fields; never the password hash
    public Map<String, Object> toUserDetails(User user) {
        Map<String, Object> userDetails = new LinkedHashMap<>();
        userDetails.put("id", user.getId());
        userDetails.put("username", user.getUsername());
        userDetails.put("email", user.getEmail());
        userDetails.put("userType", user.getUserType());
        userDetails.put("enabled", user.isEnabled());
        userDetails.put("locked", user.isLocked());
        userDetails.put("createdAt", user.getCreatedAt());
        return userDetails;
    }
}
