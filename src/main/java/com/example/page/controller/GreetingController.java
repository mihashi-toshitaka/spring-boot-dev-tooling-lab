package com.example.page.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Displays the basic Thymeleaf sample page. */
@Controller
public class GreetingController {

    /**
     * Displays a greeting page rendered by Thymeleaf.
     *
     * @param model values passed to the template
     * @return Thymeleaf view name
     */
    @GetMapping("/greeting")
    public String greeting(Model model) {
        model.addAttribute("title", "Spring Boot + Thymeleaf");
        model.addAttribute("message", "Thymeleafの画面表示が利用できます。");
        return "greeting";
    }
}
