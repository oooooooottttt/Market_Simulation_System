package com.project___10.demo.controller;

import com.project___10.demo.service.AIAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIAssistantService aiAssistantService;

    @PostMapping("/analyze")
    public String analyze(@RequestBody Map<String, String> params) {
        return aiAssistantService.getAIAnalysis(params.get("symbol"), params.get("message"));
    }

}