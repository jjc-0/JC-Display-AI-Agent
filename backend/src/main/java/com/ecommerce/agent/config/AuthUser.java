package com.ecommerce.agent.config;

import com.ecommerce.agent.model.User;
import org.springframework.security.core.Authentication;

public final class AuthUser {

    private AuthUser() {}

    public static User current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }

    public static String stableUserId(Authentication authentication) {
        User user = current(authentication);
        return user != null && user.getId() != null ? String.valueOf(user.getId()) : "";
    }

    public static String username(Authentication authentication) {
        User user = current(authentication);
        return user != null ? user.getUsername() : "";
    }
}
