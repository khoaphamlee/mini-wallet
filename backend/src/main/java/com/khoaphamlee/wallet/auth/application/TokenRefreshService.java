package com.khoaphamlee.wallet.auth.application;

import com.khoaphamlee.wallet.auth.domain.RefreshToken;
import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.auth.domain.UserStatus;
import com.khoaphamlee.wallet.auth.infra.JwtService;
import com.khoaphamlee.wallet.auth.infra.RefreshTokenRepository;
import com.khoaphamlee.wallet.auth.infra.UserRepository;
import com.khoaphamlee.wallet.common.error.BusinessException;
import com.khoaphamlee.wallet.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Refresh token rotation and logout (US-03, BR-13). */
@Service
public class TokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);

    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository users;
    private final JwtService jwtService;
    private final Clock clock;

    public TokenRefreshService(
            RefreshTokenRepository refreshTokens,
            RefreshTokenService refreshTokenService,
            UserRepository users,
            JwtService jwtService,
            Clock clock) {
        this.refreshTokens = refreshTokens;
        this.refreshTokenService = refreshTokenService;
        this.users = users;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /**
     * Exchanges a refresh token for a new token pair. The old refresh token stops working (rotation).
     *
     * <p>{@code noRollbackFor}: by default an exception rolls the transaction back. Here one failure
     * path must still be saved: when a revoked token is reused, all the user's tokens are revoked
     * before the error is thrown. Every other failure happens before anything is written.
     *
     * @throws BusinessException {@code INVALID_TOKEN} for unknown, revoked, expired or disabled-user tokens
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens
                .findByTokenHash(RefreshTokenService.hash(rawToken))
                .orElseThrow(TokenRefreshService::invalidToken);

        if (token.getRevokedAt() != null) {
            // A token that was already used or logged out is back. Someone may have stolen it, so
            // end every session of this user; the real user simply logs in again.
            int revoked = refreshTokens.revokeAllForUser(token.getUserId(), now);
            log.warn("Revoked refresh token reused for user {}; revoked {} active tokens", token.getUserId(), revoked);
            throw invalidToken();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        User user = users.findById(token.getUserId()).orElseThrow(TokenRefreshService::invalidToken);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidToken();
        }

        // Two requests with the same token at the same moment: only one wins this update.
        if (refreshTokens.revokeIfActive(token.getId(), now) == 0) {
            throw invalidToken();
        }

        String accessToken =
                jwtService.createAccessToken(user.getId(), user.getRole().name());
        String newRefreshToken = refreshTokenService.issue(user.getId());
        return new LoginResult(accessToken, newRefreshToken, jwtService.accessTokenTtl());
    }

    /**
     * Revokes the refresh token if it belongs to the current user. Unknown tokens, other users' tokens
     * and tokens already revoked are ignored, so logout always succeeds and reveals nothing.
     */
    @Transactional
    public void logout(String rawToken, UUID currentUserId) {
        refreshTokens
                .findByTokenHash(RefreshTokenService.hash(rawToken))
                .filter(token -> token.getUserId().equals(currentUserId))
                .ifPresent(token -> refreshTokens.revokeIfActive(token.getId(), clock.instant()));
    }

    private static BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_TOKEN);
    }
}
