package com.velorix.sentinel.service;

import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.dto.auth.ChangePasswordRequest;
import com.velorix.sentinel.dto.auth.DeleteAccountRequest;
import com.velorix.sentinel.dto.auth.ForgotPasswordRequest;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.dto.auth.ResendVerificationRequest;
import com.velorix.sentinel.dto.auth.ResetPasswordRequest;
import com.velorix.sentinel.dto.auth.UserResponse;
import com.velorix.sentinel.dto.auth.VerifyEmailRequest;
import com.velorix.sentinel.entity.User;

/**
 * Contract for the authentication module: credential verification, token issuance,
 * revocation and account recovery.
 */
public interface AuthService {

    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithOAuth(
            String provider,
            String providerId,
            String email,
            String firstName,
            String lastName,
            boolean emailVerified);

    User authenticateOAuthUser(
            String provider,
            String providerId,
            String email,
            String firstName,
            String lastName,
            boolean emailVerified);

    String createOAuthExchangeCode(User user);

    AuthResponse exchangeOAuthCode(String code);

    void linkOAuth(String provider, String providerId);

    void linkOAuth(User user, String provider, String providerId);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(String refreshToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(ChangePasswordRequest request);

    void deleteAccount(DeleteAccountRequest request);

    UserResponse verifyEmail(VerifyEmailRequest request);

    void resendVerification(ResendVerificationRequest request);

    UserResponse currentUser();
}
