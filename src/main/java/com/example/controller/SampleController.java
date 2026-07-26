package com.example.controller;

import com.example.service.SampleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SampleController
 * SampleController
 */
@RestController
public class SampleController {

    private final SampleService sampleService;

    /**
     * SampleController
     * @param testService sampleService
     */
    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    /**
     * getTest
     */
    @GetMapping("/test")
    public String getTest() {
        sampleService.service();
        // return testService.test;
        return sampleService.test;
    }
}
