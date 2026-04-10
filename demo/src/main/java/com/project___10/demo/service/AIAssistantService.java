package com.project___10.demo.service;

public interface AIAssistantService {
    //根据股票代码和用户提问，获取 AI 的进一步分析
    String getAIAnalysis(String symbol, String question);
}