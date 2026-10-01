package com.auth.auth.shared;

// Used for authorization. Spring sees it as "ROLE_" + name, e.g. ROLE_ADMIN
public enum UserType {
    REGULAR_USER,
    ADMIN
}
