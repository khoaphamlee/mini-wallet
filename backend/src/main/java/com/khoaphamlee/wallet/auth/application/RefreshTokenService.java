package com.khoaphamlee.wallet.auth.application;

import com.khoaphamlee.wallet.auth.domain.RefreshToken;
import com.khoaphamlee.wallet.auth.infra.JwtProperties;
import com.khoaphamlee.wallet.auth.infra.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues refresh tokens (BR-13: valid 7 days). Rotation and revocation are added in issue #6.
 *
 * <p>A refresh token is 256 random bits, not a JWT: the server must be able to revoke it, so it has to
 * be looked up in the database anyway, and a random value carries no readable data.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates and stores a new refresh token for the user. Returns the raw value; only its hash is saved. */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(
                RefreshToken.issue(userId, hash(rawToken), clock.instant().plus(properties.refreshTokenTtl())));
        return rawToken;
    }

    /**
     * SHA-256 as 64 hex characters. A fast hash is fine here, unlike for passwords: the token is 256
     * random bits, so there is nothing to guess, and lookups by hash must be quick.
     */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", ex);
        }
    }
}
