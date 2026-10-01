package com.auth.auth.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.auth.auth.model.User;
import com.auth.auth.repository.UserRepository;
import com.auth.auth.shared.UserType;

// Registration always creates REGULAR_USER, so the first ADMIN is created here at startup.
// Only runs when ADMIN_EMAIL and ADMIN_PASSWORD are set, and only if that email is not taken yet.
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminAccountInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            return;
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }

        if (userRepository.existsByUsername("admin")) {
            logger.warn("Username 'admin' is already taken; admin account {} was not created", email);
            return;
        }

        if (adminPassword.length() < 8) {
            throw new IllegalStateException("admin.password (ADMIN_PASSWORD) must be at least 8 characters long");
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setUserType(UserType.ADMIN);
        userRepository.save(admin);

        logger.info("Admin account created: {}", email);
    }
}
