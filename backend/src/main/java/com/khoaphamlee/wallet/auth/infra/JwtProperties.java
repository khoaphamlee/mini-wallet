package com.khoaphamlee.wallet.auth.infra;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings read from the {@code app.jwt.*} keys in application.yaml.
 *
 * @param secret key used to sign and verify tokens with HS256; must be at least 32 bytes
 * @param accessTokenTtl how long an access token stays valid (BR-13: 15 minutes)
 * @param refreshTokenTtl how long a refresh token stays valid (BR-13: 7 days)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    /** Fails at startup instead of later, when the first token is signed with a weak key. */
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
