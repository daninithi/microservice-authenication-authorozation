package com.product.product.security;

import java.util.UUID;

// The logged-in user, read from the JWT. This service has no users table.
public record AuthUser(UUID userId, String email, String role) {
}
