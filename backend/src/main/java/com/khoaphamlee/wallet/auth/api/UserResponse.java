package com.khoaphamlee.wallet.auth.api;

import com.khoaphamlee.wallet.auth.domain.User;
import java.time.Instant;
import java.util.UUID;

/** Public view of a user, as defined by {@code UserResponse} in {@code docs/openapi.yaml}. Never exposes hashes. */
public record UserResponse(
        UUID id, String email, String phone, String fullName, String role, boolean pinSet, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getRole().name(),
                user.isPinSet(),
                user.getCreatedAt());
    }
}
