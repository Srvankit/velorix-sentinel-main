package com.velorix.sentinel.controller;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.service.VerificationTokenService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint for E2E and integration verification.
 * Strictly forbidden and never registered under the "prod" profile.
 */
@RestController
@RequestMapping({ApiConstants.AUTH_BASE + "/test", ApiConstants.API_V1 + "/auth/test"})
@Profile({"test", "e2e"})
@Hidden
public class TestSupportController {

    private final UserRepository userRepository;
    private final VerificationTokenService verificationTokenService;

    public TestSupportController(
            UserRepository userRepository,
            VerificationTokenService verificationTokenService) {
        this.userRepository = userRepository;
        this.verificationTokenService = verificationTokenService;
    }

    @PostMapping("/verification-token")
    public ResponseEntity<ApiResponse<Map<String, String>>> issueVerificationToken(@RequestParam String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Test user not found: " + email));
        String rawToken = verificationTokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("token", rawToken, "email", user.getEmail()),
                "Test verification token generated"));
    }
}
