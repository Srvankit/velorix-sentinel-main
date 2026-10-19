package com.velorix.sentinel.security;

import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.exception.UnauthorizedException;
import com.velorix.sentinel.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place that resolves the caller behind the security context.
 *
 * <p>Every owner-scoped query in the platform goes through {@link #ownerScope()} so the
 * "admins see everything, users see their own data" rule is expressed exactly once.</p>
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The authenticated principal, or empty when the request is anonymous. */
    public Optional<UserPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /** The authenticated principal or a 401. */
    public UserPrincipal requirePrincipal() {
        return principal().orElseThrow(() -> new UnauthorizedException("You are not signed in"));
    }

    /** The persisted, still-active account behind the security context, or a 401. */
    @Transactional(readOnly = true)
    public User requireUser() {
        UUID id = requirePrincipal().id();
        return userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Your account is no longer active"));
    }

    public UUID requireUserId() {
        return requirePrincipal().id();
    }

    public boolean isAdmin() {
        return principal().map(principal -> Role.ADMIN.authority().equals(principal.role())).orElse(false);
    }

    /**
     * Owner filter applied to repository queries: {@code null} for administrators
     * (unrestricted) and the caller's id for everyone else.
     */
    public UUID ownerScope() {
        UserPrincipal principal = requirePrincipal();
        return Role.ADMIN.authority().equals(principal.role()) ? null : principal.id();
    }
}
