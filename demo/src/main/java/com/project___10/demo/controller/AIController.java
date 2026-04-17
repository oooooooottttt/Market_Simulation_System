package com.project___10.demo.controller;

import com.project___10.demo.service.AIAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIAssistantService aiAssistantService;

    @PostMapping("/analyze")
    public String analyze(@RequestBody Map<String, String> params) {
        return aiAssistantService.getAIAnalysis(
                params.getOrDefault("symbol", ""),
                params.getOrDefault("message", "")
        );
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalyze(@RequestBody Map<String, String> params) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() ->
                aiAssistantService.streamAIAnalysis(
                        params.getOrDefault("symbol", ""),
                        params.getOrDefault("message", ""),
                        emitter
                )
        );
        return emitter;
    }
}
