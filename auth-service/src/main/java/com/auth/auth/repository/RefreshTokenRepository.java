package com.auth.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.auth.auth.model.RefreshToken;
import com.auth.auth.model.User;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t
            set t.revoked = true
            where t.user = :user and t.revoked = false
            """)
    int revokeAllByUser(@Param("user") User user);

    // Expired tokens are useless (even for reuse detection), so they can be deleted
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.user = :user")
    int deleteAllByUser(@Param("user") User user);
}
