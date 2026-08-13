package com.example.page.controller;

import com.example.page.service.UserRegistrationService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Displays custom authentication pages and accepts new account registrations. */
@Controller
public class AuthenticationPageController {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRegistrationService registrationService;

    AuthenticationPageController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Displays the login page. Authentication itself is handled by Spring Security.
     *
     * @return Thymeleaf view name
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Displays the account registration page.
     *
     * @param model values passed to the template
     * @return Thymeleaf view name
     */
    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("username", "");
        model.addAttribute("errors", Map.of());
        return "signup";
    }

    /**
     * Validates the submitted values and creates a database-backed user account.
     *
     * @param username requested login username
     * @param password requested password
     * @param passwordConfirmation repeated password
     * @param model values passed to the template when validation fails
     * @return redirect or Thymeleaf view name
     */
    @PostMapping("/signup")
    public String signup(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String passwordConfirmation,
            Model model) {
        Map<String, String> errors = validate(username, password, passwordConfirmation);
        if (!errors.isEmpty()) {
            return showSignup(model, username, errors);
        }

        try {
            if (!registrationService.register(username, password)) {
                errors.put("username", "このユーザー名は既に使用されています。");
                return showSignup(model, username, errors);
            }
        } catch (DuplicateKeyException exception) {
            errors.put("username", "このユーザー名は既に使用されています。");
            return showSignup(model, username, errors);
        }

        return "redirect:/login?registered";
    }

    private static Map<String, String> validate(String username, String password, String passwordConfirmation) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (username.isBlank()) {
            errors.put("username", "ユーザー名を入力してください。");
        } else if (username.length() < 3 || username.length() > 50) {
            errors.put("username", "ユーザー名は3〜50文字で入力してください。");
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            errors.put("username", "ユーザー名には半角英数字、ピリオド、ハイフン、アンダースコアを使用できます。");
        }

        if (password.isBlank()) {
            errors.put("password", "パスワードを入力してください。");
        } else if (password.length() < 8) {
            errors.put("password", "パスワードは8文字以上で入力してください。");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            errors.put("password", "パスワードはUTF-8で72バイト以内にしてください。");
        }

        if (!password.equals(passwordConfirmation)) {
            errors.put("passwordConfirmation", "確認用パスワードが一致しません。");
        }
        return errors;
    }

    private static String showSignup(Model model, String username, Map<String, String> errors) {
        model.addAttribute("username", username);
        model.addAttribute("errors", errors);
        return "signup";
    }
}
