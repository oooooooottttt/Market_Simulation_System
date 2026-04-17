package com.project___10.demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIAssistantServiceImpl implements AIAssistantService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${serviceurl}")
    private String pythonAiUrl;

    @Value("${serviceurlstream:http://localhost:5000/api/ai/chat/stream}")
    private String pythonAiStreamUrl;

    @Override
    public String getAIAnalysis(String symbol, String question) {
        log.info("Calling Python AI sync endpoint, symbol={}, question={}", symbol, question);

        try {
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("symbol", symbol);
            requestMap.put("question", question);

            ResponseEntity<Map> response = restTemplate.postForEntity(pythonAiUrl, requestMap, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return String.valueOf(response.getBody().getOrDefault("reply", ""));
            }
        } catch (Exception e) {
            log.error("Sync AI call failed: {}", e.getMessage());
            return "AI assistant is temporarily unavailable.";
        }
        return "No valid reply was returned.";
    }

    @Override
    public void streamAIAnalysis(String symbol, String question, SseEmitter emitter) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "symbol", symbol,
                    "question", question
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiStreamUrl))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                sendEmitterEvent(emitter, "error", Map.of(
                        "type", "error",
                        "content", "Python AI stream endpoint returned status " + response.statusCode()
                ));
                emitter.complete();
                return;
            }

            try (
                    InputStream inputStream = response.body();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String payload = line.substring(5).trim();
                    if (payload.isEmpty()) {
                        continue;
                    }

                    Map<String, Object> event = objectMapper.readValue(
                            payload,
                            new TypeReference<Map<String, Object>>() {
                            }
                    );

                    String type = String.valueOf(event.getOrDefault("type", "message"));
                    sendEmitterEvent(emitter, type, event);

                    if ("done".equals(type)) {
                        emitter.complete();
                        return;
                    }
                }
            }

            emitter.complete();
        } catch (Exception e) {
            log.error("Streaming AI call failed: {}", e.getMessage());
            try {
                sendEmitterEvent(emitter, "error", Map.of(
                        "type", "error",
                        "content", "AI assistant is temporarily unavailable."
                ));
            } catch (Exception ignored) {
                log.warn("Failed to send SSE error payload: {}", ignored.getMessage());
            }
            emitter.completeWithError(e);
        }
    }

    private void sendEmitterEvent(SseEmitter emitter, String eventName, Map<String, Object> event) throws Exception {
        emitter.send(
                SseEmitter.event()
                        .name(eventName)
                        .data(event)
        );
    }
}
