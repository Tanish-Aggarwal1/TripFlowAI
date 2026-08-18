package com.tripflow.backend.controller;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.config.RefreshTokenProperties;
import com.tripflow.backend.dto.AuthResponse;
import com.tripflow.backend.dto.LoginRequest;
import com.tripflow.backend.dto.RefreshResponse;
import com.tripflow.backend.dto.RegisterRequest;
import com.tripflow.backend.exception.InvalidRefreshTokenException;
import com.tripflow.backend.exception.InvalidRequestException;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.service.AuthService;
import com.tripflow.backend.service.RefreshTokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "Registration and login — issues the JWT used by every other endpoint")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Non-simple header a cross-site form or image tag cannot set, so requiring it forces a
     * CORS preflight that only an allow-listed origin survives. This is the CSRF control for
     * the cookie-authenticated refresh endpoint — SameSite cannot carry it, because the
     * deployed frontend and backend are different subdomains of a shared PaaS suffix and are
     * therefore cross-site. */
    private static final String CSRF_GATE_HEADER = "X-Requested-With";

    /** One constant for both directions. @CookieValue needs a compile-time constant, so a bound
     * property could only ever be honoured on the write side — and a server that issues one
     * cookie name while looking for another 401s every refresh and logs nothing. */
    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenProperties refreshTokenProperties;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;

    @Operation(summary = "Register a new account", description = "Creates a user and returns a JWT, same shape as login.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiterService.checkLimit("register:" + httpRequest.getRemoteAddr(), rateLimitProperties.register());
        AuthResponse response = authService.register(request);
        attachRefreshCookie(httpResponse, refreshTokenService.issue(response.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log in", description = "Validates credentials and returns a JWT plus an httpOnly refresh cookie.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiterService.checkLimit("login:" + httpRequest.getRemoteAddr(), rateLimitProperties.login());
        AuthResponse response = authService.login(request);
        attachRefreshCookie(httpResponse, refreshTokenService.issue(response.userId()));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh the access token",
            description = "Redeems the httpOnly refresh cookie once, returning a new access token and a rotated cookie. "
                    + "Requires an X-Requested-With header.")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String rawRefreshToken,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        requireCsrfGateHeader(httpRequest);
        rateLimiterService.checkLimit("refresh:" + httpRequest.getRemoteAddr(), rateLimitProperties.refresh());
        if (rawRefreshToken == null) {
            throw new InvalidRefreshTokenException();
        }

        RefreshTokenService.RotatedSession session = refreshTokenService.rotate(rawRefreshToken);
        attachRefreshCookie(httpResponse,
                new RefreshTokenService.IssuedToken(session.rawRefreshToken(), session.refreshExpiresAt()));
        return ResponseEntity.ok(new RefreshResponse(session.accessToken(), "Bearer", session.accessExpiresAt()));
    }

    @Operation(summary = "Log out this device",
            description = "Revokes the presented refresh cookie and clears it, leaving the user's other devices "
                    + "signed in. Always 204, whether or not a valid cookie was presented. "
                    + "Requires an X-Requested-With header.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String rawRefreshToken,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        requireCsrfGateHeader(httpRequest);
        if (rawRefreshToken != null) {
            refreshTokenService.revoke(rawRefreshToken);
        }
        // Cleared unconditionally, and 204 regardless of what the cookie was worth: logout must be
        // idempotent and must not become an oracle for whether a given cookie was still valid.
        httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie("").maxAge(Duration.ZERO).build().toString());
        return ResponseEntity.noContent().build();
    }

    private void requireCsrfGateHeader(HttpServletRequest request) {
        if (request.getHeader(CSRF_GATE_HEADER) == null) {
            throw new InvalidRequestException("Missing required " + CSRF_GATE_HEADER + " header");
        }
    }

    private void attachRefreshCookie(HttpServletResponse response, RefreshTokenService.IssuedToken issued) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(issued.rawToken())
                .maxAge(Duration.between(Instant.now(), issued.expiresAt()))
                .build()
                .toString());
    }

    /** Single source of the cookie's attributes so the clearing cookie matches the issuing one
     * exactly — a browser treats a mismatched name/path/attribute set as a different cookie and
     * silently keeps the original, which is the classic way a logout fails to log anyone out.
     *
     * <p>Never sets a Domain attribute: an explicit parent-suffix domain would expose the cookie
     * to every other tenant hosted on the same PaaS suffix. Host-only is the point. */
    private ResponseCookie.ResponseCookieBuilder refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path(refreshTokenProperties.cookiePath());
    }
}
