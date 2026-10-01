package com.auth.auth.dto;

import lombok.Getter;
import lombok.Setter;

// Optional body for clients that do not use cookies (e.g. mobile apps, Postman)
@Getter
@Setter
public class RefreshTokenRequest {
    private String refreshToken;
}
