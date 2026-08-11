package com.example.page.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GreetingController.class)
@WithMockUser(username = "test-user")
class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void displaysGreetingPage() throws Exception {
        mockMvc.perform(get("/greeting"))
                .andExpect(status().isOk())
                .andExpect(view().name("greeting"))
                .andExpect(model().attribute("title", "Spring Boot + Thymeleaf"))
                .andExpect(model().attribute("message", "Thymeleafの画面表示が利用できます。"))
                .andExpect(model().attribute("username", "test-user"))
                .andExpect(content().string(containsString("Spring Boot + Thymeleaf")))
                .andExpect(content().string(containsString("test-user")))
                .andExpect(content().string(containsString("/css/app.css")));
    }
}
