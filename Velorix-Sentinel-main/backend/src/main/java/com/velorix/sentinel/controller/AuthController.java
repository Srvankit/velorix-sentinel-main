package com.velorix.sentinel.controller;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.dto.auth.ForgotPasswordRequest;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.dto.auth.ResendVerificationRequest;
import com.velorix.sentinel.dto.auth.ResetPasswordRequest;
import com.velorix.sentinel.dto.auth.UserResponse;
import com.velorix.sentinel.dto.auth.VerifyEmailRequest;
import com.velorix.sentinel.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication surface: registration, login, token lifecycle,
 * email verification and password recovery.
 */
@RestController
@RequestMapping(ApiConstants.AUTH_BASE)
@Tag(name = "Authentication", description = "Registration, login, token lifecycle and password recovery")
@SecurityRequirements
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates an unverified account and dispatches a verification email.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("Registration requested");
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "Account created successfully", HttpStatus.CREATED));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a token pair")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.debug("Login requested");
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Authenticated successfully"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new access token pair")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token rotated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or revoked refresh token")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request), "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the supplied refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Send a password reset link",
            description = "Always returns 200 to avoid disclosing whether an account exists.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "If the account exists, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset with an emailed token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated successfully"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address with the emailed token")
    public ResponseEntity<ApiResponse<UserResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyEmail(request), "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Send a fresh verification email",
            description = "Always returns 200 to avoid disclosing whether an account exists.")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(
                ApiResponse.success(null, "If the account exists, a verification link has been sent"));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(authService.currentUser(), "Profile loaded"));
    }
}
