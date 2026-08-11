package com.example.security;

import static org.springframework.security.config.Customizer.withDefaults;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Configures form login, HTTP Basic authentication, and database-backed users. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/public")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(withDefaults())
                .logout(withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    JdbcUserDetailsManager userDetailsManager(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }

    @Bean
    ApplicationRunner initialUserInitializer(
            JdbcUserDetailsManager users,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.initial-user.username}") String username,
            @Value("${app.security.initial-user.password}") String password) {
        return args -> {
            if (!users.userExists(username)) {
                users.createUser(User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("USER")
                        .build());
            }
        };
    }
}
