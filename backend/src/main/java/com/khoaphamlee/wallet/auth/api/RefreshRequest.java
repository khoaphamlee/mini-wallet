package com.khoaphamlee.wallet.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}. */
public record RefreshRequest(@NotBlank String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
