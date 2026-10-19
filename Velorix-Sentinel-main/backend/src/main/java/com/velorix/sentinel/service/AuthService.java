package com.velorix.sentinel.service;

import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.dto.auth.ForgotPasswordRequest;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.dto.auth.ResendVerificationRequest;
import com.velorix.sentinel.dto.auth.ResetPasswordRequest;
import com.velorix.sentinel.dto.auth.UserResponse;
import com.velorix.sentinel.dto.auth.VerifyEmailRequest;

/**
 * Contract for the authentication module: credential verification, token issuance,
 * revocation and account recovery.
 */
public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithOAuth(String email, String firstName, String lastName);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(String refreshToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    UserResponse verifyEmail(VerifyEmailRequest request);

    void resendVerification(ResendVerificationRequest request);

    UserResponse currentUser();
}
