package com.project___10.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIAssistantServiceImpl implements AIAssistantService {

    private final RestTemplate restTemplate;

    @Value("${serviceurl}")
    private String PYTHON_AI_URL;

    @Override
    public String getAIAnalysis(String symbol, String question) {
        log.info("呼叫 Python 端: {}, {}", symbol, question);

        try {
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("symbol", symbol);
            requestMap.put("question", question);

            ResponseEntity<Map> response = restTemplate.postForEntity(PYTHON_AI_URL, requestMap, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("reply");
            }
        } catch (Exception e) {
            log.error("接口调用失败: {}", e.getMessage());
            return "AI 助手离线中，请检查 5000 端口服务。";
        }
        return "未获取到有效回复。";
    }
}