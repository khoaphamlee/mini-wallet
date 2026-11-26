package com.khoaphamlee.wallet.auth.application;

import java.time.Duration;

/** What a successful login produces. The controller turns it into the API response. */
public record LoginResult(String accessToken, String refreshToken, Duration accessTokenTtl) {

    @Override
    public String toString() {
        return "LoginResult[accessToken=***, refreshToken=***, accessTokenTtl=" + accessTokenTtl + "]";
    }
}
