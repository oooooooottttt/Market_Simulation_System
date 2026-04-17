package com.project___10.demo.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AIAssistantService {
    String getAIAnalysis(String symbol, String question);

    void streamAIAnalysis(String symbol, String question, SseEmitter emitter);
}
