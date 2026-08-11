package com.example.api.controller;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides public and HTTP Basic protected API examples. */
@RestController
public class AuthenticationApiController {

    /**
     * Returns a response without authentication.
     *
     * @return public API response
     */
    @GetMapping("/api/public")
    public Map<String, String> publicApi() {
        return Map.of("message", "認証なしでアクセスできます。");
    }

    /**
     * Returns a response to an authenticated HTTP Basic user.
     *
     * @param principal authenticated user
     * @return protected API response
     */
    @GetMapping("/api/private")
    public Map<String, String> privateApi(Principal principal) {
        return Map.of("message", "Basic認証でアクセスしました。", "username", principal.getName());
    }
}
