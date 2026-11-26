package com.khoaphamlee.wallet.auth.api;

import com.khoaphamlee.wallet.auth.application.LoginResult;

/**
 * Token pair returned by login (and by refresh in issue #6), as {@code TokenResponse} in
 * {@code docs/openapi.yaml}.
 *
 * @param expiresIn access token lifetime in seconds
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static TokenResponse from(LoginResult result) {
        return new TokenResponse(
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.accessTokenTtl().toSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + "]";
    }
}
