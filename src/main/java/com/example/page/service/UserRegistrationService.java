package com.example.page.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates database-backed user accounts for form login. */
@Service
public class UserRegistrationService {

    private final JdbcUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;

    UserRegistrationService(JdbcUserDetailsManager users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an enabled user with the USER role unless the username is already registered.
     *
     * @param username login username
     * @param rawPassword password before encoding
     * @return {@code true} when the user was created, or {@code false} when it already exists
     */
    @Transactional
    public boolean register(String username, String rawPassword) {
        if (users.userExists(username)) {
            return false;
        }

        users.createUser(User.withUsername(username)
                .password(passwordEncoder.encode(rawPassword))
                .roles("USER")
                .build());
        return true;
    }
}
