package com.example.api.controller;

import com.example.api.service.SampleService;
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
     *
     * @param sampleService sample service
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
