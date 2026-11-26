package com.khoaphamlee.wallet.auth.api;

import com.khoaphamlee.wallet.auth.application.LoginService;
import com.khoaphamlee.wallet.auth.application.RegistrationService;
import com.khoaphamlee.wallet.auth.application.TokenRefreshService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints. All are public except logout, which needs an access token. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final TokenRefreshService tokenRefreshService;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            TokenRefreshService tokenRefreshService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.tokenRefreshService = tokenRefreshService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(
                registrationService.register(request.email(), request.phone(), request.fullName(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(loginService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(tokenRefreshService.refresh(request.refreshToken()));
    }

    /** {@code @AuthenticationPrincipal} gives the verified access token; its subject is the user id. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request, @AuthenticationPrincipal Jwt accessToken) {
        tokenRefreshService.logout(request.refreshToken(), UUID.fromString(accessToken.getSubject()));
    }
}
