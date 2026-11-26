package com.khoaphamlee.wallet.auth.application;

import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.auth.domain.UserStatus;
import com.khoaphamlee.wallet.auth.infra.JwtService;
import com.khoaphamlee.wallet.auth.infra.UserRepository;
import com.khoaphamlee.wallet.common.error.BusinessException;
import com.khoaphamlee.wallet.common.error.ErrorCode;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logs a user in with email and password (US-02).
 *
 * <p>Every failure, whether the email is unknown, the password is wrong or the account is disabled,
 * gives the same {@code INVALID_CREDENTIALS} error, so nobody can find out which emails are
 * registered.
 */
@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /** BCrypt hash of a random value, checked when the email is unknown. See {@link #login}. */
    private final String dummyPasswordHash;

    public LoginService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        Optional<User> found = users.findByEmail(email.strip().toLowerCase(Locale.ROOT));

        if (found.isEmpty()) {
            // BCrypt takes around 100 ms. Without this call an unknown email would answer much faster
            // than a wrong password, and the response time alone would reveal who is registered.
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash()) || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken =
                jwtService.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResult(accessToken, refreshToken, jwtService.accessTokenTtl());
    }
}
