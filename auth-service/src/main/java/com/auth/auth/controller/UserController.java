package com.auth.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth.auth.dto.ResponseMessage;
import com.auth.auth.model.User;
import com.auth.auth.service.UserService;
import com.auth.auth.shared.ResponseStatus;

// Any logged-in user (see SecurityConfig)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    // @AuthenticationPrincipal gives the User that JwtAuthenticationFilter put into Spring Security
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new ResponseMessage<>(ResponseStatus.Success, userService.toUserDetails(user)));
    }
}
