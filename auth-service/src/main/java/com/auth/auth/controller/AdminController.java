package com.auth.auth.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth.auth.dto.ResponseMessage;
import com.auth.auth.model.User;
import com.auth.auth.service.UserService;
import com.auth.auth.shared.ResponseStatus;

// Only ADMIN users can reach this controller (rule is in SecurityConfig)
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(new ResponseMessage<>(ResponseStatus.Success, userService.getAllUsers()));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id, @AuthenticationPrincipal User currentAdmin) {
        userService.deleteUser(id, currentAdmin);
        return ResponseEntity.ok(new ResponseMessage<>(ResponseStatus.Success, "User deleted successfully"));
    }
}
