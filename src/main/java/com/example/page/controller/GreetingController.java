package com.example.page.controller;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Displays the basic Thymeleaf sample page. */
@Controller
public class GreetingController {

    /**
     * Redirects the application root to the authenticated page.
     *
     * @return redirect view name
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/greeting";
    }

    /**
     * Displays a greeting page rendered by Thymeleaf.
     *
     * @param model values passed to the template
     * @param principal authenticated user
     * @return Thymeleaf view name
     */
    @GetMapping("/greeting")
    public String greeting(Model model, Principal principal) {
        model.addAttribute("title", "Spring Boot + Thymeleaf");
        model.addAttribute("message", "Thymeleafの画面表示が利用できます。");
        model.addAttribute("username", principal.getName());
        return "greeting";
    }
}
