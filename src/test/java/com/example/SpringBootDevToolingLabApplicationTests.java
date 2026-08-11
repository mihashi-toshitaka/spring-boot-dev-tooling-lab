package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresTestcontainersConfiguration.class,
    ValkeyTestcontainersConfiguration.class,
    SpringBootDevToolingLabApplicationTests.SessionRoutesConfiguration.class
})
class SpringBootDevToolingLabApplicationTests {

    private static final String SESSION_PATH = "/test-support/session";
    private static final String SESSION_ATTRIBUTE_NAME = "testValue";
    private static final String SESSION_ATTRIBUTE_VALUE = "stored-in-valkey";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Value("${spring.session.data.redis.namespace}")
    private String sessionNamespace;

    @Test
    void storesAndRestoresHttpSessionInValkey() throws Exception {
        MvcResult createResult = mockMvc.perform(
                        post(SESSION_PATH).with(user("session-test")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SESSION"))
                .andReturn();

        String sessionId = createResult.getResponse().getContentAsString();
        Cookie sessionCookie = Objects.requireNonNull(createResult.getResponse().getCookie("SESSION"));
        String sessionKey = sessionNamespace + ":sessions:" + sessionId;

        try {
            assertThat(redisTemplate.hasKey(sessionKey)).isTrue();
            assertThat(redisTemplate.opsForHash().hasKey(sessionKey, "sessionAttr:" + SESSION_ATTRIBUTE_NAME))
                    .isTrue();

            mockMvc.perform(get(SESSION_PATH).cookie(sessionCookie).with(user("session-test")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(SESSION_ATTRIBUTE_VALUE));
        } finally {
            sessionRepository.deleteById(sessionId);
        }

        assertThat(redisTemplate.hasKey(sessionKey)).isFalse();
    }

    @Test
    void redirectsProtectedPageToDefaultLoginPage() throws Exception {
        mockMvc.perform(get("/greeting")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void authenticatesFormLoginAndStoresSecurityContextInValkey() throws Exception {
        MvcResult loginResult = mockMvc.perform(formLogin().user("user").password("password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("SESSION"))
                .andReturn();

        Cookie sessionCookie = Objects.requireNonNull(loginResult.getResponse().getCookie("SESSION"));
        String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
        String sessionKey = sessionNamespace + ":sessions:" + sessionId;

        try {
            assertThat(redisTemplate.hasKey(sessionKey)).isTrue();
            assertThat(redisTemplate.opsForHash().hasKey(sessionKey, "sessionAttr:SPRING_SECURITY_CONTEXT"))
                    .isTrue();

            mockMvc.perform(get("/greeting").cookie(sessionCookie))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(">user</strong>")))
                    .andExpect(content().string(containsString("としてログイン中")));
        } finally {
            sessionRepository.deleteById(sessionId);
        }
    }

    @Test
    void allowsPublicApiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("認証なしでアクセスできます。"))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void requiresBasicAuthenticationForPrivateApi() throws Exception {
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Basic")))
                .andExpect(cookie().doesNotExist("SESSION"));

        mockMvc.perform(get("/api/private").with(httpBasic("user", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("SESSION"));

        mockMvc.perform(get("/api/private").with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Basic認証でアクセスしました。"))
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SessionRoutesConfiguration {

        @Bean
        RouterFunction<ServerResponse> sessionRoutes() {
            return RouterFunctions.route()
                    .POST(SESSION_PATH, request -> {
                        HttpSession session = request.servletRequest().getSession();
                        session.setAttribute(SESSION_ATTRIBUTE_NAME, SESSION_ATTRIBUTE_VALUE);
                        return ServerResponse.ok().body(session.getId());
                    })
                    .GET(SESSION_PATH, request -> {
                        HttpSession session = request.servletRequest().getSession(false);
                        if (session == null) {
                            return ServerResponse.notFound().build();
                        }
                        Object value = session.getAttribute(SESSION_ATTRIBUTE_NAME);
                        return value == null
                                ? ServerResponse.notFound().build()
                                : ServerResponse.ok().body(value);
                    })
                    .build();
        }
    }
}
