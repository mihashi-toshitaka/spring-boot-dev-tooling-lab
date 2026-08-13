package com.example;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@Tag("playwright")
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"app.security.initial-user.username=user", "app.security.initial-user.password=password"})
@Import({PostgresTestcontainersConfiguration.class, ValkeyTestcontainersConfiguration.class})
class PlaywrightE2eTest {

    @LocalServerPort
    private int serverPort;

    @Test
    void logsInAndDisplaysGreetingPage() {
        String baseUrl = "http://127.0.0.1:" + serverPort;

        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch()) {
            Page page = browser.newPage();

            page.navigate(baseUrl + "/greeting");

            assertThat(page).hasURL(baseUrl + "/login");
            assertThat(page.locator("h1")).hasText("ログイン");

            page.getByLabel("ユーザー名").fill("user");
            page.getByLabel("パスワード").fill("password");
            page.getByRole(AriaRole.BUTTON).click();

            assertThat(page)
                    .hasURL(Pattern.compile("^http://127[.]0[.]0[.]1:" + serverPort + "/greeting(?:[?]continue)?$"));
            assertThat(page.locator("h1")).hasText("Spring Boot + Thymeleaf");
            assertThat(page.locator(".session-card")).containsText("user としてログイン中");
        }
    }
}
